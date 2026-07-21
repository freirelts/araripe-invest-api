# Conteúdos de estudo na web

Este arquivo reúne referências para estudar os conceitos usados no Araripe Invest. A lista prioriza fontes educacionais, institucionais ou amplamente usadas como apoio para acompanhamento educacional de ativos, análise fundamentalista, valuation, gestão de risco, backtest, dados de mercado e análise macro.

Última verificação dos links: 2026-07-08.

## Ordem indicada de estudo

1. Conceitos básicos de mercado e renda variável.
2. Acompanhamento educacional de ativos e construção de modelo de estudo.
3. Análise fundamentalista: receita, lucro, margens, dívida e caixa.
4. Valuation: P/L, P/VP, EV/EBITDA, dividend yield e margem de segurança.
5. Risco educacional: diversificação conceitual, preço de referência do estudo, margem de segurança e pontos de atenção.
6. Indicadores técnicos de longo prazo: médias de 100/200, drawdown e volatilidade.
7. Backtest: vieses, dividendos, retorno total, drawdown e benchmark.
8. IA generativa aplicada com saída estruturada, validação e auditoria.
9. E-mails informativos com Spring Mail.
10. Dados brasileiros: B3, brapi.dev, Banco Central SGS e CVM.

## Mercado financeiro brasileiro

| Conteúdo                                                                                     | Fonte | Por que estudar                                                                   |
| -------------------------------------------------------------------------------------------- | ----- | --------------------------------------------------------------------------------- |
| [B3 Educação](https://edu.b3.com.br/)                                                        | B3    | Base educacional sobre investimentos e funcionamento do mercado brasileiro.       |
| [Cursos da B3 Educação](https://edu.b3.com.br/educacao-financeira/cursos)                    | B3    | Catálogo com cursos de renda variável, análise gráfica e análise fundamentalista. |
| [Análise Fundamentalista de Ações](https://edu.b3.com.br/w/analise-fundamentalista-de-acoes) | B3    | Apoia a lógica central do sistema.                                                |
| [Análise Gráfica para Iniciantes](https://edu.b3.com.br/w/analise-grafica-para-iniciantes)   | B3    | Ajuda a entender tendência longa e leitura básica de preço.                       |

## Análise fundamentalista e valuation

| Conteúdo                                                                                             | Fonte         | Relação com o Araripe Invest                                          |
| ---------------------------------------------------------------------------------------------------- | ------------- | --------------------------------------------------------------------- |
| [Financial Statements](https://www.investopedia.com/terms/f/financial-statements.asp)                | Investopedia  | Base para DRE, balanço e fluxo de caixa.                              |
| [Fundamental Analysis](https://www.investopedia.com/terms/f/fundamentalanalysis.asp)                 | Investopedia  | Visão geral da análise por fundamentos.                               |
| [Price-to-Earnings Ratio](https://www.investopedia.com/terms/p/price-earningsratio.asp)              | Investopedia  | Base para P/L.                                                        |
| [Price-to-Book Ratio](https://www.investopedia.com/terms/p/price-to-bookratio.asp)                   | Investopedia  | Base para P/VP.                                                       |
| [EV/EBITDA](https://www.investopedia.com/terms/e/ev-ebitda.asp)                                      | Investopedia  | Base para múltiplo EV/EBITDA.                                         |
| [Dividend Yield](https://www.investopedia.com/terms/d/dividendyield.asp)                             | Investopedia  | Ajuda a avaliar modelo de estudo de dividendos sustentáveis.                      |
| [Refresher Readings](https://www.cfainstitute.org/insights/professional-learning/refresher-readings) | CFA Institute | Conteúdos profissionais para aprofundar valuation, portfolio e risco. |

## Risco educacional e acompanhamento

| Conteúdo                                                                             | Fonte        | Relação com o Araripe Invest                |
| ------------------------------------------------------------------------------------ | ------------ | ------------------------------------------- |
| [Portfolio Management](https://www.investopedia.com/terms/p/portfoliomanagement.asp) | Investopedia | Base conceitual para entender diversificação, sem prescrição individualizada. |
| [Diversification](https://www.investopedia.com/terms/d/diversification.asp)          | Investopedia | Apoia explicações educacionais sobre concentração e risco.                   |
| [Margin of Safety](https://www.investopedia.com/terms/m/marginofsafety.asp)          | Investopedia | Base para margem de segurança e preço de referência do estudo. |
| [Drawdown](https://www.investopedia.com/terms/d/drawdown.asp)                        | Investopedia | Ajuda a entender risco de queda acumulada.  |

## Backtest e sistemas de investimento

Pontos que devem ser estudados antes de implementar o backtest:

- look-ahead bias: usar informação futura sem perceber;
- survivorship bias: testar só ativos que sobreviveram até hoje;
- overfitting: ajustar regra demais ao passado;
- dividendos e reinvestimento;
- custos operacionais;
- liquidez;
- drawdown máximo;
- CAGR;
- retorno total;
- turnover;
- comparação contra benchmark.

## Dados e fontes brasileiras

| Conteúdo                                                                                                                                              | Fonte                   | Relação com o Araripe Invest                                                  |
| ----------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | ----------------------------------------------------------------------------- |
| [brapi.dev](https://brapi.dev/)                                                                                                                       | brapi.dev               | Fonte inicial para cotações, OHLCV, dividendos, fundamentos e macro no MVP.   |
| [Documentação brapi.dev](https://brapi.dev/docs)                                                                                                      | brapi.dev               | Referência técnica para integração do `araripe-invest-api`.                   |
| [Cotações Históricas](https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/cotacoes-historicas/) | B3                      | Fonte oficial futura para séries históricas e backtests mais robustos.        |
| [Sistema Gerenciador de Séries Temporais](https://www3.bcb.gov.br/sgspub/)                                                                            | Banco Central do Brasil | Consulta de Selic, CDI, IPCA, câmbio e outras séries macro.                   |
| [Dados Abertos CVM](https://dados.cvm.gov.br/)                                                                                                        | CVM                     | Base futura para documentos, dados de companhias e validação fundamentalista. |

## IA e e-mails informativos

| Conteúdo                                                                            | Fonte  | Relação com o Araripe Invest                                                                            |
| ----------------------------------------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------- |
| [OpenAI API docs](https://developers.openai.com/api/docs)                           | OpenAI | Referência para geração de texto, saídas estruturadas e modelos.                                        |
| [Spring Boot Sending Email](https://docs.spring.io/spring-boot/reference/io/email.html) | Spring | Envio de alertas por e-mail usando `spring-boot-starter-mail`, `spring.mail.*` e `JavaMailSender`.      |

## Como usar esses conteúdos no projeto

Para cada conteúdo estudado, registrar uma decisão prática:

- qual regra do sistema ele ajuda a justificar;
- qual indicador ou filtro será afetado;
- quais limitações o conteúdo aponta;
- quais testes devem ser criados;
- quais termos precisam aparecer de forma simples no `araripe-invest-fed`.

Exemplo:

```text
Conteúdo estudado: margem de segurança
Decisão no Araripe Invest: bloquear nova modelo de estudo quando preço atual estiver acima do preço de referência do estudo.
Limitação: preço justo é estimativa e pode estar errado.
Teste necessário: ativo bom com preço caro não deve entrar como critérios atendidos.
Texto para usuário: "O ativo é bom, mas o preço atual está acima do preço de referência do estudo definido pela regra."
```

## Critério para adicionar novos conteúdos

Adicionar uma nova referência somente se ela ajudar em pelo menos uma destas decisões:

- melhorar uma modelo de estudo;
- melhorar cálculo de valuation;
- melhorar cálculo de risco analítico;
- melhorar qualidade de dados;
- reduzir falso positivo;
- explicar um termo para usuário leigo;
- melhorar backtest;
- melhorar compliance e comunicação de risco;
- melhorar explicação por IA com fontes e validação;
- melhorar e-mails informativos sobre limiares definidos pelo usuário, dados, indicadores e premissas.
