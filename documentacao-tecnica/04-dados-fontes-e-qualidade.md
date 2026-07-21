# Dados, fontes e qualidade

## Universo de ativos monitorados

Os ativos consultados no brapi.dev são os ativos cadastrados e ativos no sistema. A curadoria acontece pelo cadastro e ativação desses ativos. O objetivo é trabalhar com ativos que tenham:

- maior liquidez;
- menor spread;
- mais informação disponível;
- melhor execução de entrada e saída;
- menor risco de o usuário ficar preso em ativo sem volume.

O cadastro de ativos deve ser tratado como dado de configuração controlado pelo sistema. Ativos inativos ou não cadastrados não devem ser consultados automaticamente.

Cada ativo mantém o campo `data_collection_initialized` para indicar se a coleta inicial de dados de mercado já foi executada. Ativos novos começam com `false`; ativos já cadastrados antes da introdução desse controle são migrados como `true` para evitar uma nova carga histórica completa desnecessária.

Na execução do job `DAILY_MARKET_DATA_COLLECTION`, a janela de coleta de histórico OHLCV e dividendos depende desse campo:

- ativo com `data_collection_initialized = false`: histórico diário com `range=2y` e dividendos sem `startDate/endDate`, para fazer a carga inicial;
- ativo com `data_collection_initialized = true`: histórico diário com `range=5d` e dividendos limitados aos últimos 5 dias via `startDate` e `endDate`.

Após uma chamada processável de histórico e dividendos para um ativo novo, o backend marca `data_collection_initialized = true`. Falha ou chamada ignorada pela fonte externa não deve avançar esse estado, para permitir nova tentativa de carga inicial.

## Fontes do MVP

### brapi.dev

Uso planejado:

- cotações;
- histórico OHLCV;
- dividendos e JCP;
- perfil da empresa;
- estatísticas e múltiplos;
- dados financeiros;
- balanço patrimonial;
- DRE;
- fluxo de caixa;
- séries macroeconômicas disponíveis.

A brapi.dev se apresenta como uma API REST brasileira para dados financeiros, incluindo cotações, histórico de preços OHLCV, dividendos, fundamentos, câmbio e indicadores econômicos.

Referências:

- https://brapi.dev/
- https://brapi.dev/docs
- [Documentação brapi usada pelo projeto](14-documentacao-brapi.md)

### Fontes macroeconômicas e setoriais para IA

Uso planejado:

- contexto de juros, inflação, câmbio e crescimento;
- resumo de ambiente setorial;
- notícias econômicas e setoriais recentes relacionadas ao ativo, ao setor e ao Brasil;
- riscos macro relevantes para modelos de estudo e posições;
- melhoria da explicação da alerta informativo.

Fontes preferenciais:

- Banco Central SGS;
- CVM Dados Abertos, quando aplicável;
- B3, quando aplicável;
- brapi.dev macro, quando disponível;
- outras fontes públicas, oficiais ou institucionais aprovadas administrativamente.

O conteúdo usado pela IA deve ser tratado como evidência contextual, não como dado financeiro primário. Cada consulta precisa registrar fonte, URL ou identificador, data de coleta, trecho resumido, hash ou versão do conteúdo quando viável.

Na análise econômica de modelo de estudo com IA, não basta pedir ao modelo para "considerar o contexto econômico". A chamada precisa habilitar busca web real, consultar notícias/fontes econômicas recentes e persistir URLs externas citadas. Resposta sem URL externa auditável deve receber status inválido ou indisponível e o sistema deve seguir com o fallback determinístico.

Essa análise não deve ser etapa obrigatória do job diário. Ela deve ser solicitada por endpoint próprio a partir do ID da modelo de estudo. O backend consulta os dados necessários, monta o pacote estruturado, chama a IA, valida a resposta e persiste a análise para consulta posterior.

## Qualidade mínima de dados

Um ativo não pode gerar modelo de estudo se:

- não houver candle suficiente para calcular média de 200 períodos;
- candle da última sessão de mercado disponível estiver ausente ou fora da tolerância operacional;
- volume estiver ausente ou zerado;
- preço de fechamento estiver ausente ou inválido;
- máxima for menor que mínima;
- abertura, máxima, mínima ou fechamento forem menores ou iguais a zero;
- fonte retornar dados duplicados conflitantes;
- data do candle não corresponder a um pregão esperado;
- ativo estiver com liquidez abaixo do mínimo;
- dados fundamentalistas mínimos estiverem ausentes;
- demonstrações financeiras estiverem desatualizadas para a modelo de estudo;
- dividendos forem usados sem data base ou data de pagamento;
- dados macro estiverem vencidos;
- endpoint externo protegido falhar por token ausente;
- alerta informativo depender de IA e a resposta da IA estiver ausente, inválida, sem fontes ou incompatível com as regras determinísticas.

### Data de referência e última sessão disponível

Para modelos de estudo de acompanhamento educacional de ativos e diagnósticos de triagem, `referenceDate` representa a data operacional do processamento, não necessariamente a data exata do último pregão. O motor pode usar o candle e os indicadores técnicos mais recentes até a `referenceDate`, desde que a última sessão disponível esteja dentro de uma tolerância curta de até 4 dias corridos.

Essa tolerância evita bloquear todos os ativos em fins de semana, feriados ou pequenos atrasos de coleta. Dados de mercado mais antigos que essa janela continuam sendo tratados como atrasados e geram `DATA_QUALITY_BLOCKED`.

Dados fundamentalistas não devem ser considerados frescos apenas porque o snapshot derivado foi processado recentemente. O critério principal é a data do período contábil usado como evidência, registrada em `most_recent_quarter` no snapshot fundamentalista derivado.

Regra operacional:

- o snapshot derivado deve carregar em `most_recent_quarter` o período contábil mais recente disponível entre o dado do provedor e os demonstrativos financeiros normalizados;
- quando houver demonstrativo anual válido, o sistema deve inferir o encerramento do exercício social pela data desse demonstrativo anual;
- para períodos trimestrais, o próximo período esperado vence 45 dias após o encerramento do trimestre, acrescido de 15 dias de tolerância operacional;
- para encerramento de exercício social, o próximo período esperado vence 3 meses após o encerramento do exercício, acrescido de 15 dias de tolerância operacional;
- se `most_recent_quarter` estiver ausente, o sistema pode usar a data de processamento (`reference_date`) como fallback, mas isso é uma evidência mais fraca e deve ser tratado como risco de qualidade;
- dado fundamentalista cujo próximo período esperado já venceu deve gerar `DATA_QUALITY_BLOCKED`, impedindo modelo de estudo, associação de modelo de estudo acompanhado e alerta informativo sem base auditável.

Exemplo:

```text
referenceDate: 2026-08-30
most_recent_quarter: 2026-03-31
último encerramento anual conhecido: 2025-12-31

Próximo trimestre esperado: 2026-06-30
Prazo regulatório aproximado: 2026-08-14
Tolerância operacional: até 2026-08-29

Em 2026-08-30, ainda usar 2026-03-31 é dado fundamentalista desatualizado.
```

## Normalização de OHLCV

O sistema deve converter todos os dados externos para um formato interno único:

| Campo interno          | Descrição                                       |
| ---------------------- | ----------------------------------------------- |
| `asset_symbol`         | Código do ativo.                                |
| `trade_date`           | Data do pregão.                                 |
| `open_price`           | Abertura.                                       |
| `high_price`           | Máxima.                                         |
| `low_price`            | Mínima.                                         |
| `close_price`          | Fechamento.                                     |
| `adjusted_close_price` | Fechamento ajustado, se disponível.             |
| `volume_quantity`      | Volume em quantidade, se disponível.            |
| `volume_financial`     | Volume financeiro, se disponível ou calculável. |
| `source`               | Fonte do dado.                                  |
| `collected_at`         | Data e hora da coleta.                          |

## Normalização fundamentalista

O sistema deve registrar:

- ativo;
- fonte;
- data de referência;
- período anual ou trimestral;
- campos financeiros relevantes;
- payload original quando útil para auditoria;
- status de qualidade.

Para DRE (`/api/v2/stocks/income-statement`), a coleta diária deve consultar explicitamente `period=annual` e `period=quarterly`. Os indicadores fundamentalistas usam a DRE anual para crescimento anual, margens e rentabilidade. A DRE trimestral deve calcular crescimento trimestral de receita e lucro comparando o último trimestre válido com o mesmo trimestre do ano anterior, não contra o trimestre imediatamente anterior, para reduzir ruído sazonal. Quando a fonte omitir o tipo no payload, o período solicitado na chamada deve ser preservado no snapshot normalizado.

Campos current/TTM já enviados pela brapi.dev e persistidos no snapshot coletado com `source=brapi` e `calculation_version=collector-v1` são a fonte canônica para a visão TTM quando estiverem válidos. O snapshot derivado com `source=araripe-indicators` deve preservar esses valores e não sobrescrevê-los com cálculo anual ou trimestral. Cálculos próprios por demonstrativo devem ser usados para campos explicitamente derivados, como crescimento anual, crescimento trimestral e crescimento de EBITDA, ou como fallback auditável quando o campo current/TTM coletado estiver ausente.

### Crescimento com base negativa

Crescimento percentual tradicional só é válido quando a base anterior é positiva. O sistema não deve usar `valor_atual / valor_anterior - 1` quando `valor_anterior <= 0`, porque isso distorce viradas de prejuízo, pioras de prejuízo e períodos com base zero.

Regra implementada:

- se o valor anterior é positivo, usar crescimento percentual tradicional;
- se o valor anterior é zero, não calcular crescimento normal, exceto quando o valor atual é negativo, caso em que a deterioração pode ser registrada como -100%;
- se o valor anterior é negativo e o valor atual ficou ainda menor, registrar deterioração proporcional sobre o módulo da base anterior;
- se o valor anterior é negativo e o valor atual melhorou ou virou positivo, registrar crescimento normal como `0`, pois a melhora é qualitativa, não crescimento percentual comparável;
- virada de prejuízo para lucro pode melhorar modelo de estudo por lucro/margem positivos, mas não deve justificar prêmio de crescimento nem desbloquear valuation extremo sozinha.

Exemplo:

```text
lucro 2024: -100
lucro 2025: 20

Resultado correto:
- não registrar crescimento de +120% ou -120%;
- registrar que houve virada qualitativa;
- manter crescimento percentual normal em 0 para fins de filtros e score.
```

### Fluxo de caixa livre por período contábil

Fluxo de caixa livre persistentemente negativo deve considerar períodos contábeis distintos, não quantidade de snapshots. O mesmo demonstrativo pode gerar vários snapshots diários derivados; contar esses snapshots como períodos diferentes cria falso positivo.

Regra implementada:

- preferir o histórico de `CASH_FLOW` anual válido em `financial_statement_snapshots`;
- extrair `freeCashFlow` diretamente quando disponível;
- se `freeCashFlow` estiver ausente, calcular `operatingCashflow - capex`, normalizando capex positivo como saída de caixa;
- considerar no máximo os últimos quatro períodos anuais válidos;
- quando não houver demonstrativos de fluxo de caixa suficientes, usar fallback em `fundamental_snapshots`, deduplicando por `most_recent_quarter` ou, na ausência dele, por `reference_date`;
- somente dois ou mais períodos contábeis distintos com FCF negativo caracterizam persistência;
- FCF atual negativo com caixa operacional não positivo continua sendo bloqueio imediato, mesmo sem dois períodos históricos.

## Ajustes de preço

Para backtests sérios, é importante saber se os preços estão ajustados por proventos, desdobramentos e grupamentos. Dados sem ajuste podem distorcer médias, retornos históricos e cálculo de dividendos.

Decisão do MVP:

- usar dados disponíveis via brapi.dev;
- registrar a fonte e a versão do dado;
- documentar se o histórico está ajustado ou não;
- bloquear comparações de backtest quando a base tiver ajuste desconhecido.

## Tratamento de falhas

Falhas externas devem gerar status rastreável, não modelos de estudo falsas.

Estados indicados:

- `PENDING`
- `SUCCESS`
- `PARTIAL_SUCCESS`
- `FAILED`
- `SKIPPED`

## Qualidade de IA

A saída de IA só pode ser usada quando:

- o prompt contém dados suficientes e não inclui segredos;
- a resposta segue o schema esperado;
- as fontes configuradas estão presentes;
- pelo menos uma URL externa de fonte econômica, institucional ou notícia recente está presente;
- números macroeconômicos citados, como Selic, IPCA, CDI e câmbio, estão coerentes com snapshots macro persistidos ou com fonte externa auditável citada por URL;
- o resumo não contradiz dados determinísticos do sistema;
- o motor financeiro valida que filtros, risco, valuation, limiar inferior de preço definido pelo usuário e limiar superior de preço definido pelo usuário continua coerentes.

Quando a IA falhar, o sistema deve:

- manter alerta informativo determinística, se ela puder ser calculada com segurança;
- marcar o enriquecimento como indisponível;
- registrar erro e latência;
- nunca inferir alerta informativo apenas por texto livre da IA.
