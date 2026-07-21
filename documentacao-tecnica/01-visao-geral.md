# Visão Geral Do Produto

## Nome

Araripe Invest.

## Objetivo

O Araripe Invest é uma plataforma educacional, informativa e analítica para estudar e acompanhar ativos brasileiros. Ele combina dados de mercado, fundamentos, indicadores, valuation educacional, risco analítico, screener e alertas informativos para ajudar o usuário a observar ativos com critérios claros.

O sistema deve ajudar o usuário a responder:

- quais ativos atendem critérios fundamentalistas mínimos;
- quais ativos têm dados suficientes, recentes e auditáveis;
- como o preço atual se compara ao preço de referência do estudo;
- qual é a faixa de observação do estudo;
- quais premissas do modelo de estudo mudaram;
- quais indicadores ou limiares definidos pelo usuário foram atingidos;
- quais fontes, datas e regras sustentam cada alerta informativo.

O produto não orienta compra, venda, manutenção, aumento, redução, alocação ou encerramento de posição. Também não executa ordens, não integra corretora, não faz suitability e não administra carteira.

## Responsabilidades Principais

1. Coletar dados de mercado, fundamentos, dividendos e macroeconomia.
2. Processar indicadores técnicos de longo prazo.
3. Processar indicadores fundamentalistas.
4. Bloquear ativos com dados incompletos, desatualizados, inconsistentes ou ilíquidos.
5. Gerar modelos de estudo auditáveis.
6. Exibir score como aderência a critérios do estudo.
7. Calcular faixa de observação do estudo, preço de referência do estudo, margem de segurança educacional e ponto de reavaliação.
8. Persistir screener e histórico de critérios.
9. Permitir cadastro informativo de posições reais declaradas pelo usuário.
10. Permitir limiares informativos de preço definidos pelo usuário por posição declarada.
11. Gerar alertas informativos após o fechamento, apenas sobre fatos observáveis.
12. Enviar um e-mail diário consolidado por cliente via Spring Mail quando houver alertas informativos.
13. Usar IA generativa da OpenAI, por recurso próprio de API, para enriquecer contexto econômico educacional com fontes e validação.
14. Controlar acesso por login, cadastro de usuários e perfis.
15. Permitir backtests e simulações genéricas sem conduta individualizada.

## Público-Alvo

Usuários que querem estudar ativos brasileiros com dados organizados, critérios objetivos e linguagem acessível. A interface deve explicar fundamentos, valuation, margem de segurança, risco analítico, qualidade de dados e limitações sem indicar o que fazer com uma posição.

## Escopo Do MVP

- Mercado brasileiro.
- Universo de ativos monitorados formado pelos ativos cadastrados e ativos no sistema.
- Candles diários.
- Dados fundamentalistas e dividendos.
- Dados macroeconômicos.
- Execução após fechamento.
- Fonte inicial: brapi.dev.
- Banco PostgreSQL.
- Backend `araripe-invest-api`.
- Frontend `araripe-invest-fed`.
- Login e cadastro de usuários.
- Perfil `CUSTOMER` para uso comum da plataforma.
- Perfil `ADMIN` para configurações gerais, cadastro de usuários e cadastro dos ativos do universo monitorado.
- Cadastro de registros informativos de posição real declarados pelo cliente.
- Associação opcional de modelo de estudo acompanhado para observação histórica.
- Alertas informativos de limiar de preço, dado atualizado, dado desatualizado, indicador fora de intervalo, premissa alterada e bloqueio de qualidade.
- E-mail diário consolidado via Spring Mail para alertas informativos das posições declaradas.
- IA generativa da OpenAI via adapter próprio da Responses API com busca web, para contexto econômico e explicações estruturadas sob demanda por modelo de estudo.
- Landing page pública com botão de login.
- Dashboards modernos com gráficos e componentes Material Design.
- Três modelos de estudo iniciais:
  - qualidade fundamentalista com preço razoável;
  - dividendos sustentáveis;
  - crescimento rentável com tendência longa saudável.

## Fora Do MVP

- Compra e venda automática.
- Integração com corretora.
- Day trade.
- Swing trade operacional.
- Opções.
- Cripto.
- Alavancagem.
- Orientação individualizada.
- IA decidindo conduta.
- Cálculo de quantidade, aporte, alocação individualizada ou ação operacional.

## Fluxo Operacional

| Horário | Etapa                                                                                         |
| ------- | --------------------------------------------------------------------------------------------- |
| 18:00   | Buscar cotações e candles do dia                                                              |
| 18:05   | Persistir candles e cotações                                                                  |
| 18:10   | Recalcular indicadores técnicos de longo prazo                                                |
| 18:15   | Atualizar fundamentos, demonstrativos e dividendos quando houver nova informação              |
| 18:20   | Atualizar contexto macro                                                                      |
| 18:25   | Rodar filtros de liquidez, qualidade e fundamentos                                            |
| 18:30   | Rodar modelos de estudo                                                                       |
| 18:35   | Calcular valuation educacional, margem de segurança e critérios do estudo                     |
| 18:40   | Gerar screener                                                                                |
| 18:45   | Varrer registros informativos de posição e gerar alertas informativos neutros                 |
| 18:55   | Persistir dados e alimentar interface                                                         |
| 19:00   | Agrupar alertas informativos por cliente e enviar um único e-mail por cliente via Spring Mail |

Se uma etapa crítica falhar, o fluxo diário deve parar antes das etapas dependentes. Não é aceitável gerar screener, alertas ou digest diário com dados antigos, incompletos ou não recalculados para a data de referência.

## Estados Do Modelo De Estudo

| Estado                             | Significado                                                             |
| ---------------------------------- | ----------------------------------------------------------------------- |
| `DADOS_INSUFICIENTES`              | Não há dados mínimos para análise.                                      |
| `EM_ESTUDO`                        | O ativo possui dados suficientes e está disponível para acompanhamento. |
| `CRITERIOS_ATENDIDOS`              | Os critérios definidos para o estudo foram atendidos.                   |
| `CRITERIOS_PARCIALMENTE_ATENDIDOS` | Parte dos critérios foi atendida, mas há pontos de atenção.             |
| `CRITERIOS_EM_ATENCAO`             | Há filtros ou critérios relevantes que exigem leitura cuidadosa.        |
| `DADOS_DESATUALIZADOS`             | O período contábil ou a fonte usada está desatualizada.                 |

`PREMISSAS_ALTERADAS` não deve ser usado como status de geração do modelo de estudo. A comunicação de premissas alteradas pertence ao alerta comparativo `STUDY_ASSUMPTION_CHANGED`, quando o modelo corrente muda em relação ao modelo aceito pelo usuário.

## Alertas Informativos

Alertas informativos são eventos factuais. Eles não dizem ao usuário o que fazer. Tipos iniciais:

| Evento                        | Significado                                            |
| ----------------------------- | ------------------------------------------------------ |
| `PRICE_THRESHOLD_REACHED`     | Um limiar de preço definido pelo usuário foi atingido. |
| `DATA_UPDATED`                | Um dado relevante foi atualizado.                      |
| `DATA_STALE`                  | Um dado relevante está desatualizado.                  |
| `INDICATOR_THRESHOLD_REACHED` | Um indicador cruzou intervalo definido no estudo.      |
| `STUDY_ASSUMPTION_CHANGED`    | Uma premissa do modelo de estudo mudou.                |
| `QUALITY_DATA_BLOCKED`        | A qualidade dos dados bloqueia o estudo ou alerta.     |

## E-Mail Diário

Ao final do processamento diário, o sistema deve montar um resumo por cliente contendo somente alertas informativos das posições declaradas.

Regras:

- enviar no máximo um e-mail por cliente por data de referência;
- não enviar e-mail quando o cliente não tiver alerta informativo;
- incluir apenas posições declaradas pelo próprio cliente;
- consolidar eventos objetivos, sem verbos de ação operacional;
- registrar status de envio, erro, tentativas, data de referência e versão da regra.
