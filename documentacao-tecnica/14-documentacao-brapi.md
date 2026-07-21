# Documentação brapi.dev usada pelo Araripe Invest

Última análise: 2026-07-07.

Fonte oficial:

- https://brapi.dev/docs

## Decisão de integração

O `araripe-invest-api` deve chamar a brapi.dev somente pelo backend. O token nunca deve ser exposto no `araripe-invest-fed`.

Em produção, usar header:

```http
Authorization: Bearer ${BRAPI_API_TOKEN}
```

A documentação da brapi informa que a URL base é:

```text
https://brapi.dev/api
```

Os endpoints v2 usados pelo Araripe Invest ficam no padrão:

```text
https://brapi.dev/api/v2/...
```

## Rotas que vamos usar

| Uso no Araripe Invest       | Endpoint brapi                                         | Prioridade |
| --------------------------- | ------------------------------------------------------ | ---------: |
| Validar ativos e cobertura  | `GET /api/v2/tickers` e `GET /api/v2/tickers/coverage` |        MVP |
| Cotação atual               | `GET /api/v2/stocks/quote`                             |        MVP |
| Histórico OHLCV             | `GET /api/v2/stocks/historical`                        |        MVP |
| Dividendos/JCP/bonificações | `GET /api/v2/stocks/dividends`                         |        MVP |
| Perfil da empresa           | `GET /api/v2/stocks/profile`                           |        MVP |
| Estatísticas e múltiplos    | `GET /api/v2/stocks/statistics`                        |        MVP |
| Dados financeiros agregados | `GET /api/v2/stocks/financial-data`                    |        MVP |
| Balanço patrimonial         | `GET /api/v2/stocks/balance-sheet`                     |        MVP |
| DRE                         | `GET /api/v2/stocks/income-statement`                  |        MVP |
| Fluxo de caixa              | `GET /api/v2/stocks/cash-flow`                         |        MVP |
| Séries macroeconômicas      | `GET /api/v2/macro`                                    |        MVP |
| Séries macro disponíveis    | `GET /api/v2/macro/available`                          |        MVP |

## Padrão de consulta multiativo

Para o Araripe Invest, o padrão preferencial é consultar múltiplos ativos por chamada sempre que o endpoint aceitar o parâmetro `symbols`.

Como o limite de tickers por requisição varia por plano na brapi.dev, o backend deve consultar no máximo 5 símbolos por chamada externa. O serviço de coleta deve dividir o universo ativo em blocos de 5 e executar o fluxo de endpoints para cada bloco, registrando a coleta por lote. O adapter brapi também deve manter o limite como proteção defensiva para chamadas diretas fora do fluxo principal.

Formato:

```text
symbols=PETR4%2CVALE3
```

Equivalente lógico:

```text
symbols=PETR4,VALE3
```

O retorno vem em `results[]`, com um item por ativo. O processamento interno deve:

- iterar por `results[]`;
- preservar `requestedSymbol`, `symbol` e `changed`;
- tratar sucesso ou falha por ativo, sem descartar o lote inteiro quando apenas um ativo apresentar problema;
- persistir `requestedAt`, `took`, fonte, endpoint, parâmetros e status de qualidade;
- persistir dados normalizados por ativo e por tipo de dado, mesmo quando a chamada externa for em lote.

Nos detalhes por rota abaixo, os blocos JSON podem mostrar apenas um trecho do retorno para evitar repetição. O padrão operacional do `araripe-invest-api` deve ser o processamento multiativo descrito nesta seção.

## 1. Cotação atual

Uso:

- preço atual;
- volume;
- variação diária;
- market cap quando disponível;
- faixa de 52 semanas.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/quote?symbols=PETR4%2CVALE3" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "shortName": "PETR4",
        "longName": "Petroleo Brasileiro SA Pfd", // Nome completo da empresa. Pfd significa preferred, ou seja, ação preferencial.
        "currency": "BRL",
        "regularMarketPrice": 37.77, // Último preço conhecido da ação no mercado regular
        "regularMarketDayHigh": 38.02, //Maior preço negociado no dia
        "regularMarketDayLow": 37.61, // Menor preço negociado no dia
        "regularMarketDayRange": "37.61 - 38.02", // Faixa de negociação do dia, combinando mínima e máxima.
        "regularMarketChange": -0.48, // Variação absoluta em reais em relação ao fechamento anterior. Nesse caso, queda de R$ 0,48.
        "regularMarketChangePercent": -1.25, // Variação percentual em relação ao fechamento anterior: queda de 1,25%.
        "regularMarketTime": "2026-07-07T13:15:30.000Z",
        "marketCap": 512063965177, // Capitalização de mercado estimada da empresa = preço da ação × quantidade de ações emitidas
        "regularMarketVolume": 20811400, // Quantidade de ações negociadas no dia até o momento da consulta
        "regularMarketPreviousClose": 37.77, // Preço de fechamento do pregão anterior.
        "regularMarketOpen": 37.95, // Preço da primeira negociação, ou preço de abertura do dia
        "fiftyTwoWeekRange": "29.31 - 50.69", // Faixa entre o menor e o maior preço dos últimos 12 meses
        "fiftyTwoWeekLow": 29.31, // Menor preço registrado nas últimas 52 semanas
        "fiftyTwoWeekHigh": 50.69, // Maior preço registrado nas últimas 52 semanas
        "logourl": "https://icons.brapi.dev/icons/PETR4.svg"
      }
    },
    {
      "requestedSymbol": "VALE3",
      "symbol": "VALE3",
      "changed": false,
      "data": {
        "shortName": "VALE3",
        "longName": "Vale S.A.",
        "currency": "BRL",
        "regularMarketPrice": 77.79,
        "regularMarketDayHigh": 78.78,
        "regularMarketDayLow": 77.5,
        "regularMarketDayRange": "77.5 - 78.78",
        "regularMarketChange": -1.05,
        "regularMarketChangePercent": -1.33,
        "regularMarketTime": "2026-07-07T13:15:30.000Z",
        "marketCap": 324301159091,
        "regularMarketVolume": 12389100,
        "regularMarketPreviousClose": 77.69,
        "regularMarketOpen": 78.29,
        "fiftyTwoWeekRange": "52.37 - 91.62",
        "fiftyTwoWeekLow": 52.37,
        "fiftyTwoWeekHigh": 91.62,
        "logourl": "https://icons.brapi.dev/icons/VALE3.svg"
      }
    }
  ],
  "requestedAt": "2026-07-07T13:15:59.454Z",
  "took": 664
}
```

Campos internos derivados:

- `current_price`;
- `market_cap`;
- `volume_quantity`;
- `price_change_percent`;
- `fifty_two_week_low`;
- `fifty_two_week_high`;
- `quote_time`.

## 2. Histórico OHLCV

Uso:

- candles diários;
- médias de longo prazo;
- retorno de 6/12 meses;
- volatilidade;
- drawdown;
- backtest.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/historical?symbols=PETR4&range=1mo&interval=1d&sortOrder=asc" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "usedInterval": "1d",
        "usedRange": "1mo",
        "historicalDataPrice": [
          {
            "date": 1780887600,
            "open": 41.2,
            "high": 41.32,
            "low": 40.83,
            "close": 41.22,
            "volume": 34043600,
            "adjustedClose": 41.22
          },
          {
            "date": 1780974000,
            "open": 40.88,
            "high": 41.37,
            "low": 40.7,
            "close": 41.17,
            "volume": 56680400,
            "adjustedClose": 41.17
          },
          {
            "date": 1781060400,
            "open": 41.21,
            "high": 42.04,
            "low": 41.01,
            "close": 41.65,
            "volume": 44953200,
            "adjustedClose": 41.65
          }
        ]
      }
    }
  ],
  "requestedAt": "2026-07-07T12:58:58.355Z",
  "took": 236
}
```

Parâmetros relevantes segundo a documentação:

- `symbols`: tickers separados por vírgula;
- `range`: `1d`, `5d`, `1mo`, `6mo`, `1y`, `5y`, `10y`, `max`, entre outros;
- `interval`: `1d`, `1wk`, `1mo`, entre outros;
- `startDate` e `endDate`: datas em `YYYY-MM-DD`;
- `sortOrder`: `asc` ou `desc`.

Uso operacional no Araripe Invest:

- ativo sem carga inicial: `range=2y`, `interval=1d`, `sortOrder=asc`;
- ativo com carga inicial já feita: `range=5d`, `interval=1d`, `sortOrder=asc`.

## 3. Dividendos, JCP e eventos

Uso:

- modelo de estudo de dividendos sustentáveis;
- histórico de proventos;
- validação de recorrência;
- backtest com dividendos.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/dividends?symbols=PETR4&sortOrder=desc" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "cashDividends": [
          {
            "assetIssued": "BRPETRACNPR6",
            "paymentDate": "2026-08-20T03:00:00.000Z",
            "rate": 0.350486,
            "relatedTo": "",
            "approvedOn": null,
            "isinCode": "BRPETRACNPR6",
            "label": "JCP",
            "lastDatePrior": "2026-06-01T03:00:00.000Z",
            "remarks": ""
          },
          {
            "assetIssued": "BRPETRACNPR6",
            "paymentDate": "2026-06-22T03:00:00.000Z",
            "rate": 0.01314955,
            "relatedTo": "",
            "approvedOn": null,
            "isinCode": "BRPETRACNPR6",
            "label": "RENDIMENTO",
            "lastDatePrior": "2026-04-22T03:00:00.000Z",
            "remarks": ""
          }
        ],
        "stockDividends": [
          {
            "assetIssued": "BRPETRACNPR6",
            "factor": 2,
            "completeFactor": "2 para 1",
            "approvedOn": "2008-04-25T03:00:00.000Z",
            "isinCode": "BRPETRACNPR6",
            "label": "DESDOBRAMENTO",
            "lastDatePrior": "2008-04-25T03:00:00.000Z",
            "remarks": ""
          }
        ],
        "subscriptions": []
      }
    }
  ],
  "requestedAt": "2026-07-07T12:58:58.803Z",
  "took": 153
}
```

Parâmetros relevantes segundo a documentação:

- `symbols`;
- `startDate`;
- `endDate`;
- `sortBy`: `paymentDate`, `lastDatePrior`, `approvedOn` ou `rate`;
- `sortOrder`: `asc` ou `desc`.

Uso operacional no Araripe Invest:

- ativo sem carga inicial: sem `startDate/endDate`, preservando a carga completa disponível;
- ativo com carga inicial já feita: `startDate` e `endDate` cobrindo os últimos 5 dias, com `sortOrder=desc`.

## 4. Perfil da empresa

Uso:

- setor;
- indústria;
- CNPJ;
- descrição;
- logo;
- dados cadastrais.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/profile?symbols=PETR4" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "sector": "Energia",
        "industry": "Petróleo e Gás Integrado",
        "longBusinessSummary": "A Petróleo Brasileiro S.A. (Petrobras) é uma sociedade de economia mista brasileira, controlada pela União Federal, com ações negociadas na B3 (PETR3/PETR4) e ADRs na NYSE (PBR/PBR.A). Fundada em 1953, a companhia atua de forma integrada na...",
        "name": null,
        "logoUrl": "https://icons.brapi.dev/icons/PETR4.svg",
        "cnpj": "33000167000101",
        "website": "https://petrobras.com.br",
        "city": null,
        "state": null,
        "country": null
      }
    }
  ],
  "requestedAt": "2026-07-07T12:58:58.995Z",
  "took": 2
}
```

## 5. Estatísticas e múltiplos

Uso:

- P/L;
- P/VP;
- EV/EBITDA;
- beta;
- dividend yield;
- market cap;
- lucro por ação;
- book value.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/statistics?symbols=PETR4&mode=current" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido para `PETR4` em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "enterpriseValue": 1116184500000, // O Enterprise Value tenta representar quanto custaria adquirir toda a operação da empresa, considerando também suas dívidas e seu caixa
        "profitMargins": 0.21689811, // É a margem líquida
        "floatShares": 4410960400, // É o free float, ou quantidade estimada de ações disponíveis para negociação no mercado
        "sharesOutstanding": 12888733000, // É a quantidade total de ações emitidas e em circulação
        "beta": 0.37399918, // O beta mede quanto a ação historicamente oscila em relação ao mercado de referência
        "bookValue": 34.540943, // É o valor patrimonial por ação, ou VPA
        "priceToBook": 1.0934849, // É o indicador Preço sobre Valor Patrimonial, conhecido como P/VP
        "mostRecentQuarter": "2026-03-31", // É a data de encerramento do trimestre financeiro mais recente utilizado pela API
        "earningsQuarterlyGrowth": -0.07274065, // Representa o crescimento trimestral do lucro, normalmente comparando o trimestre mais recente com o mesmo trimestre do ano anterior.
        "netIncomeToCommon": 107583000000, // É o lucro líquido atribuível aos acionistas comuns
        "trailingEps": 8.347058, // É o EPS dos últimos 12 meses, equivalente ao LPA, lucro por ação
        "earningsPerShare": 8.347058, // Também representa o lucro por ação
        "trailingPE": 5.0137424, // É o P/L calculado com base no lucro dos últimos 12 meses
        "pegRatio": 0.041203145, // É o indicador PEG, que relaciona o P/L com a taxa de crescimento dos lucros
        "enterpriseToRevenue": 2.2409248, // Significa que o valor da firma representa aproximadamente 2,24 vezes a receita anual considerada
        "enterpriseToEbitda": 4.834395, // Significa que o valor da firma equivale a aproximadamente 4,83 vezes o EBITDA anual
        "52WeekChange": 0.28394577, // Isso indica uma valorização aproximada de 28,39% em 52 semanas
        "lastDividendDate": "2026-06-01",
        "yield": 0.06, // Representa um rendimento de dividendos de 6%
        "marketCap": 486807440000, // É o valor de mercado da empresa:
        "dividendYield": 0.06 // Também representa um dividend yield de 6%
      }
    }
  ],
  "requestedAt": "2026-07-07T12:58:59.130Z",
  "took": 2
}
```

Parâmetros relevantes:

- `mode=current`: indicador atual/TTM;
- `mode=history`: série anual ou trimestral;
- `period=annual` ou `period=quarterly`.

## 6. Dados financeiros agregados

Uso:

- receita;
- EBITDA;
- dívida;
- caixa;
- margens;
- crescimento;
- ROA;
- ROE;
- fluxo de caixa livre.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/financial-data?symbols=PETR4&mode=current" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido para `PETR4` em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": {
        "totalCash": 47600000000, // Caixa total e aplicações de alta liquidez
        "totalCashPerShare": 3.6931481, // Caixa por ação = caixa total ÷ número de ações
        "ebitda": 230884000000, // O EBITDA representa o resultado antes de: juros;impostos;depreciação;amortização;
        "totalDebt": 676977000000, // Dívida total informada pela fonte
        "quickRatio": 0.48622373, // Liquidez seca = ativos circulantes mais líquidos ÷ passivos circulantes
        "currentRatio": 0.74290836, // Liquidez corrente = ativo circulante ÷ passivo circulante
        "totalRevenue": 498091000000, // Receita total acumulada no período considerado, provavelmente últimos 12 meses
        "debtToEquity": 1.5206507, // Dívida / patrimônio líquido = dívida total ÷ patrimônio líquido
        "returnOnAssets": 0.08670072, // Retorno sobre ativos ROA = lucro líquido ÷ ativos totais
        "returnOnEquity": 0.24267222, // Retorno sobre patrimônio líquido ROE = lucro líquido ÷ patrimônio líquido
        "grossProfits": 235891000000, // Lucro bruto = receita líquida - custos dos produtos ou serviços
        "freeCashflow": 80740000000, // Fluxo de caixa livre = fluxo de caixa operacional - investimentos
        "operatingCashflow": 194970000000, // Fluxo de caixa operacional
        "earningsGrowth": 1.2168349, // Crescimento do lucro no período mais recente
        "revenueGrowth": 0.0037057786, // Crescimento recente da receita
        "earningsGrowthAnnual": 2.0084958, // Crescimento anual do lucro
        "revenueGrowthAnnual": 0.013691123, // Crescimento anual da receita
        "grossMargins": 0.47359017, // Margem bruta = lucro bruto ÷ receita
        "ebitdaMargins": 0.46353778, // Margem EBITDA = EBITDA ÷ receita
        "operatingMargins": 0.28881872, // Margem operacional = lucro operacional ÷ receita
        "profitMargins": 0.21689811 // Margem líquida = lucro líquido ÷ receita
      }
    }
  ],
  "requestedAt": "2026-07-07T12:58:59.265Z",
  "took": 2
}
```

## 7. Balanço patrimonial

Uso:

- caixa;
- ativos;
- passivos;
- patrimônio;
- dívida;
- análise de solidez.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/balance-sheet?symbols=PETR4&period=annual" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": [
        {
          "type": "yearly", // Indica que os dados são anuais
          "endDate": "2025-12-31", // Data de encerramento do balanço patrimonial:
          "cash": 35608000000, // Caixa e equivalentes de caixa
          "shortTermInvestments": 15000001000, // Investimentos financeiros de curto prazo
          "totalCurrentAssets": 140026000000, // Ativo circulante total
          "propertyPlantEquipment": 924624000000, // É o Property, Plant and Equipment, conhecido contabilmente como ativo imobilizado
          "totalAssets": 1223389000000, // Total de ativos = Passivos + Patrimônio líquido
          "totalLiab": 805802000000, // Total de passivos, representa as obrigações da empresa com terceiros
          "intangibleAssets": 13885000000 // Ativos intangíveis, são ativos que possuem valor econômico, mas não têm forma física
        }
      ]
    }
  ],
  "requestedAt": "2026-07-07T12:58:59.405Z",
  "took": 5
}
```

## 8. DRE

Uso:

- receita;
- lucro bruto;
- despesas;
- EBIT;
- lucro líquido;
- margens.

O Araripe Invest deve coletar a DRE duas vezes no job diário:

- `period=annual`, para crescimento anual, margens anuais e indicadores derivados de fundamentos;
- `period=quarterly`, para crescimento trimestral de receita, lucro e EBITDA.

Sem a chamada trimestral, os campos `quarterlyRevenueGrowth` e `quarterlyEarningsGrowth` calculados a partir de demonstrativos ficam sem insumo e devem permanecer nulos.

Request anual:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/income-statement?symbols=PETR4&period=annual" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Request trimestral:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/income-statement?symbols=PETR4&period=quarterly" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": [
        {
          "type": "yearly",
          "endDate": "2025-12-31",
          "totalRevenue": 497549000000, // Receita total da empresa no período
          "costOfRevenue": -260551000000, // Custo associado à geração da receita
          "grossProfit": 236998000000, // Lucro bruto = receita - custo da receita
          "totalOperatingExpenses": -91370000000, // Despesas operacionais totais, também aparecem negativas porque reduzem o resultado.
          "operatingIncome": 145628000000, // Resultado operacional. Representa o lucro gerado pelas atividades principais da empresa antes do resultado financeiro e dos impostos.
          "ebit": 145628000000, // Earnings Before Interest and Taxes, Lucro antes de juros e impostos
          "incomeBeforeTax": 150599000000, // Lucro antes do imposto de renda
          "incomeTaxExpense": -39994000000, // Despesa com imposto de renda e contribuição social
          "netIncome": 110605000000 // Lucro líquido. É o resultado final depois de custos, despesas, resultado financeiro e impostos.
        }
      ]
    }
  ],
  "requestedAt": "2026-07-07T12:58:59.567Z",
  "took": 8
}
```

## 9. Fluxo de caixa (DFC)

Uso:

- caixa operacional;
- fluxo de investimento;
- fluxo de financiamento;
- fluxo de caixa livre;
- sustentabilidade de dividendos.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/stocks/cash-flow?symbols=PETR4&period=annual" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "data": [
        {
          "type": "yearly",
          "endDate": "2025-12-31",
          "operatingCashFlow": 200333000000, // Fluxo de caixa gerado pelas atividades operacionais. Esse valor representa o dinheiro efetivamente gerado pelas operações principais da empresa.
          "incomeFromOperations": 253975000000, // Resultado ou caixa derivado das operações antes de determinados ajustes.
          "investmentCashFlow": -86114000000, // Fluxo de caixa das atividades de investimento.
          "financingCashFlow": -97122000000, // Fluxo de caixa das atividades de financiamento.
          "increaseOrDecreaseInCash": 15354000000, // Aumento líquido do caixa durante o ano.
          "initialCashBalance": 20254000000, // Caixa existente no início do exercício.
          "finalCashBalance": 35608000000, // Caixa existente ao final do exercício.
          "freeCashFlow": 114219000000 // Fluxo de caixa livre.
        }
      ]
    }
  ],
  "requestedAt": "2026-07-07T12:58:59.707Z",
  "took": 2
}
```

## 10. Macroeconomia

Uso:

- Selic;
- CDI;
- IPCA/IPCA 12 meses;
- IGP-M;
- atividade econômica;
- contexto macro para score.

Request:

```bash
curl -X GET "https://brapi.dev/api/v2/macro?symbols=selic,ipca12m&limit=3" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real reduzido obtido pela documentação em 2026-07-07:

```json
{
  "results": [
    {
      "series": {
        "slug": "selic",
        "name": "Taxa Selic",
        "unit": "percentPerYear",
        "frequency": "daily",
        "category": "interestRate"
      },
      "observations": [
        {
          "date": "2026-04-30",
          "value": 14.5
        },
        {
          "date": "2026-04-29",
          "value": 14.75
        },
        {
          "date": "2026-04-28",
          "value": 14.75
        }
      ]
    },
    {
      "series": {
        "slug": "ipca12m",
        "name": "IPCA acumulado 12 meses",
        "unit": "percent",
        "frequency": "monthly",
        "category": "inflation"
      },
      "observations": [
        {
          "date": "2026-03-01",
          "value": 4.14
        },
        {
          "date": "2026-02-01",
          "value": 3.81
        },
        {
          "date": "2026-01-01",
          "value": 4.44
        }
      ]
    }
  ]
}
```

Slugs relevantes segundo a documentação:

- `selic`;
- `cdi`;
- `ipca`;
- `ipca12m`;
- `igpm`;
- `tr`;
- `inpc`;
- `igpdi`;
- `ibcbr`;
- `pibmensal`;
- `desemprego`;
- `m1`;
- `m4`;
- `reservas`.

### 10.1. Séries macro disponíveis

Uso:

- descobrir slugs disponíveis;
- montar tela/admin de configuração macro;
- validar se `selic`, `ipca12m`, `cdi` e demais séries existem.

Request executada com token:

```bash
curl -X GET "https://brapi.dev/api/v2/macro/available?q=selic" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido em 2026-07-07:

```json
{
  "results": [
    {
      "slug": "selic",
      "name": "Taxa Selic",
      "description": "Taxa básica de juros da economia brasileira, definida pelo COPOM (Comitê de Política Monetária) do Banco Central. É a referência para todas as demais taxas de juros do país.",
      "unit": "percentPerYear",
      "frequency": "daily",
      "category": "interestRate",
      "startDate": "1999-03-05"
    },
    {
      "slug": "selicovernight",
      "name": "Selic Overnight",
      "description": "Taxa Selic efetiva diária (over), apurada nas operações compromissadas com títulos públicos federais. Diferente da Selic meta — reflete a taxa praticada no mercado.",
      "unit": "percentPerDay",
      "frequency": "daily",
      "category": "interestRate",
      "startDate": "1986-03-04"
    }
  ],
  "categories": [
    "interestRate",
    "inflation",
    "monetary",
    "activity",
    "labor",
    "external"
  ],
  "count": 2,
  "requestedAt": "2026-07-07T12:59:00.287Z",
  "took": 0
}
```

## 11. Tickers e cobertura

Uso:

- validar ticker antes de cadastrar ativo no universo monitorado;
- verificar se o ativo tem cobertura para cotação, histórico, dividendos e fundamentos;
- evitar cadastrar ativo sem dados suficientes para acompanhamento educacional de ativos.

Requests:

```bash
curl -X GET "https://brapi.dev/api/v2/tickers/coverage?symbols=PETR4" \
  -H "Authorization: Bearer ${BRAPI_API_TOKEN}"
```

Retorno real autenticado reduzido de busca em 2026-07-07:

```json
{
  "results": [
    {
      "symbol": "PETR4",
      "name": "PETROLEO BRASILEIRO S.A. PETROBRAS",
      "longName": "Petroleo Brasileiro SA Pfd",
      "assetType": "stock",
      "subType": "stock",
      "exchange": "B3",
      "currency": "BRL",
      "sector": "Energy Minerals",
      "isActive": true,
      "logoUrl": "https://icons.brapi.dev/icons/PETR4.svg",
      "quote": {
        "lastPrice": 37.77,
        "changePercent": -1.25,
        "volume": 20811400,
        "marketCap": 512063965177
      }
    },
    {
      "symbol": "PETR4F",
      "name": "PETROLEO BRASILEIRO S.A. PETROBRAS",
      "longName": "Petroleo Brasileiro SA Pfd",
      "assetType": "stock",
      "subType": "stock",
      "exchange": "B3",
      "currency": "BRL",
      "sector": "Energy Minerals",
      "isActive": true,
      "logoUrl": "https://icons.brapi.dev/icons/BRAPI.svg",
      "quote": {
        "lastPrice": 37.79,
        "changePercent": -1.18,
        "volume": 113323,
        "marketCap": 512063965177
      }
    }
  ],
  "requestedAt": "2026-07-07T12:59:17.482Z",
  "took": 386
}
```

Observação: na validação, `GET /api/v2/tickers?q=PETR4` não filtrou como esperado e retornou a lista padrão. Para busca textual de ticker, usar `search=PETR4`.

Retorno real autenticado reduzido de cobertura em 2026-07-07:

```json
{
  "results": [
    {
      "requestedSymbol": "PETR4",
      "symbol": "PETR4",
      "changed": false,
      "status": "available",
      "assetType": "stock",
      "subType": "stock",
      "availableData": {
        "ticker": true,
        "quote": true,
        "historical": true,
        "stockDividends": true,
        "fiiDividends": false,
        "profile": true,
        "statistics": true,
        "financialStatements": true,
        "fiiIndicators": false,
        "fiiReports": false,
        "fiiPortfolio": false,
        "fiiProperties": false
      },
      "recommendedEndpoints": {
        "ticker": "/api/v2/tickers?search=PETR4",
        "quote": "/api/v2/stocks/quote?symbols=PETR4",
        "historical": "/api/v2/stocks/historical?symbols=PETR4&range=1y&interval=1d",
        "dividends": "/api/v2/stocks/dividends?symbols=PETR4",
        "profile": "/api/v2/stocks/profile?symbols=PETR4",
        "statistics": "/api/v2/stocks/statistics?symbols=PETR4",
        "financialData": "/api/v2/stocks/financial-data?symbols=PETR4",
        "balanceSheet": "/api/v2/stocks/balance-sheet?symbols=PETR4",
        "incomeStatement": "/api/v2/stocks/income-statement?symbols=PETR4",
        "cashFlow": "/api/v2/stocks/cash-flow?symbols=PETR4",
        "valueAdded": "/api/v2/stocks/value-added?symbols=PETR4"
      }
    }
  ],
  "requestedAt": "2026-07-07T12:59:01.155Z",
  "took": 37
}
```

Observação: `availableData.statistics=true` e `availableData.financialStatements=true` indicam cobertura do ativo. Mesmo assim, a implementação deve validar a resposta real dos endpoints obrigatórios antes de gerar modelo de estudo.

## Tratamento de erros

O `araripe-invest-api` deve mapear erros da brapi para estados internos:

| HTTP/code                             | Tratamento                                                                                         |
| ------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `400 BAD_REQUEST`                     | Falha de contrato. Registrar e bloquear ativo na execução.                                         |
| `401 MISSING_TOKEN` ou `UNAUTHORIZED` | Falha de autenticação. Registrar e bloquear regra dependente.                                      |
| `403 FORBIDDEN`                       | Permissão indisponível ou contrato externo rejeitado. Registrar falha e bloquear regra dependente. |
| `404 NOT_FOUND`                       | Ativo ou recurso indisponível. Marcar como sem cobertura.                                          |
| `429 RATE_LIMIT_EXCEEDED`             | Aplicar retry limitado/backoff e bloquear regra dependente se o dado obrigatório não for obtido.   |
| `500 INTERNAL_SERVER_ERROR`           | Falha externa. Registrar e tentar novamente em execução futura.                                    |

## Variáveis de ambiente

```text
BRAPI_BASE_URL=https://brapi.dev/api
BRAPI_API_TOKEN=...
BRAPI_TIMEOUT_SECONDS=20
BRAPI_RETRY_MAX_ATTEMPTS=3
```

## Observações para implementação

- Usar token por header, nunca por query string em produção.
- Chamar brapi apenas pelo `araripe-invest-api`.
- Preservar `requestedSymbol`, `symbol` e `changed`, porque a brapi pode resolver tickers antigos para novos.
- Persistir `requestedAt`, `took`, fonte e status de qualidade.
- Não gerar modelo de estudo se endpoint obrigatório da modelo de estudo falhar.
- Separar dados de mercado, fundamentos, demonstrações e macro em adapters diferentes, mesmo usando o mesmo provedor.
