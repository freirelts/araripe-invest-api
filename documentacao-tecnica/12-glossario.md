# Glossário Financeiro e Funcional

Este glossário reúne os conceitos funcionais do sistema, os campos retornados pelas APIs financeiras e os principais termos contábeis, fundamentalistas, de mercado e de fluxo de caixa discutidos.

> **Convenções**
>
> - Valores monetários devem ser interpretados na moeda informada pelo campo `currency`.
> - Percentuais retornados como números decimais devem ser multiplicados por 100 para exibição.
> - Campos de custos, despesas e saídas de caixa podem ser retornados com sinal negativo.
> - As fórmulas apresentadas são simplificadas. A metodologia exata pode variar conforme a fonte dos dados.
> - Indicadores devem ser analisados em conjunto, considerando setor, histórico, ciclo econômico, qualidade dos dados e período de referência.

---

## 52-Week Change — Variação em 52 Semanas

Variação percentual do preço do ativo ao longo dos últimos 12 meses.

**Campo comum:** `52WeekChange`

**Fórmula:**

```text
Variação em 52 semanas =
(Preço atual - Preço de 52 semanas atrás)
÷ Preço de 52 semanas atrás
```

Para exibição percentual:

```text
Percentual = valor decimal × 100
```

Normalmente representa apenas a variação do preço. Pode não incluir dividendos, juros sobre capital próprio ou outros proventos.

---

## 52-Week High — Máxima de 52 Semanas

Maior preço registrado pelo ativo nas últimas 52 semanas.

**Campo comum:** `fiftyTwoWeekHigh`

É útil para comparar o preço atual com o maior preço observado no período, mas não determina se o ativo está caro ou barato.

---

## 52-Week Low — Mínima de 52 Semanas

Menor preço registrado pelo ativo nas últimas 52 semanas.

**Campo comum:** `fiftyTwoWeekLow`

É útil como referência histórica, mas não representa necessariamente valor justo ou margem de segurança.

---

## 52-Week Range — Faixa de 52 Semanas

Intervalo entre a mínima e a máxima do ativo nas últimas 52 semanas.

**Campo comum:** `fiftyTwoWeekRange`

**Representação:**

```text
Mínima de 52 semanas - Máxima de 52 semanas
```

Exemplo:

```text
29,31 - 50,69
```

---

## Alert — Alerta Informativo

Evento factual gerado pelo sistema quando uma condição previamente definida é atingida.

Pode ser acionado por:

- limiar de preço;
- atualização ou desatualização de dados;
- indicador fora de intervalo;
- premissa alterada;
- mudança em um modelo de estudo;
- inconsistência ou bloqueio de qualidade.

O alerta deve comunicar um fato ou condição observável, sem determinar compra, venda, aumento de posição ou encerramento de uma tese.

---

## Annual Data — Dados Anuais

Informações contábeis consolidadas para um exercício social completo.

**Campo comum:**

```text
type = "yearly"
```

A data de encerramento costuma ser informada em `endDate`.

---

## Annual Earnings Growth — Crescimento Anual do Lucro

Variação do lucro anual em relação ao período anual comparável anterior.

**Campo comum:** `earningsGrowthAnnual`

**Fórmula:**

```text
Crescimento anual do lucro =
(Lucro atual - Lucro anterior)
÷ Lucro anterior
```

Para exibição:

```text
Percentual = valor decimal × 100
```

Um crescimento de `2,00`, por exemplo, representa aumento de aproximadamente 200%, fazendo o novo valor chegar a cerca de 300% do valor anterior.

---

## Annual Revenue Growth — Crescimento Anual da Receita

Variação percentual da receita anual em relação ao período anual comparável anterior.

**Campo comum:** `revenueGrowthAnnual`

**Fórmula:**

```text
Crescimento anual da receita =
(Receita atual - Receita anterior)
÷ Receita anterior
```

---

## Asset — Ativo Contábil

Recurso controlado pela empresa com potencial de gerar benefícios econômicos futuros.

Exemplos:

- caixa;
- contas a receber;
- estoques;
- imóveis;
- máquinas;
- plataformas;
- investimentos;
- softwares;
- direitos e concessões.

---

## Asset Under Monitoring — Ativo Acompanhado

Ativo cadastrado pelo usuário para observação informativa.

Pode possuir:

- limiares de preço;
- indicadores selecionados;
- modelo de estudo associado;
- histórico de avaliações;
- alertas informativos;
- trilha de auditoria.

---

## Audit Trail — Trilha de Auditoria

Registro estruturado das informações utilizadas e das decisões tomadas pelo sistema.

Deve incluir, quando aplicável:

- fonte dos dados;
- data e hora da consulta;
- período contábil;
- versão das regras;
- parâmetros de entrada;
- resultados calculados;
- transformações realizadas;
- alertas emitidos;
- inconsistências;
- erros e exceções.

---

## Backtest — Teste Histórico

Simulação de uma regra, estratégia ou modelo utilizando dados históricos.

Serve para avaliar como uma regra teria se comportado no passado.

Não deve ser apresentado como:

- promessa de retorno;
- garantia de desempenho;
- recomendação individualizada;
- evidência suficiente de desempenho futuro.

---

## Balance Sheet — Balanço Patrimonial

Demonstração contábil que apresenta a posição patrimonial e financeira da empresa em uma data específica.

A equação fundamental é:

```text
Ativos = Passivos + Patrimônio Líquido
```

Diferentemente da DRE e da DFC, o balanço patrimonial representa uma fotografia dos saldos em uma data, não o fluxo acumulado durante todo o período.

---

## Beta — Beta

Indicador que estima a sensibilidade histórica do retorno do ativo em relação ao retorno de um índice de referência.

**Campo comum:** `beta`

Interpretação simplificada:

```text
Beta = 1
Oscilação semelhante ao mercado

Beta > 1
Tendência histórica de maior oscilação que o mercado

Beta < 1
Tendência histórica de menor oscilação que o mercado
```

O beta depende de:

- índice de referência;
- período analisado;
- frequência dos dados;
- metodologia da fonte.

O beta não mede diretamente risco operacional, financeiro, regulatório, político ou de liquidez.

---

## Book Value — Valor Patrimonial

Valor contábil do patrimônio líquido da empresa.

**Fórmula:**

```text
Valor patrimonial =
Ativos totais - Passivos totais
```

Também pode ser usado para calcular o valor patrimonial por ação.

---

## Book Value per Share — Valor Patrimonial por Ação

Parcela contábil do patrimônio líquido atribuída a cada ação.

**Campos comuns:** `bookValue` ou VPA.

**Fórmula:**

```text
Valor patrimonial por ação =
Patrimônio líquido
÷ Número de ações considerado
```

Não representa necessariamente o valor que o acionista receberia em uma liquidação.

---

## Capital Expenditure — Investimentos em Capital

Desembolsos destinados à aquisição, manutenção ou expansão de ativos de longo prazo.

Também chamado de **Capex**.

Exemplos:

- máquinas;
- equipamentos;
- plataformas;
- refinarias;
- instalações;
- infraestrutura;
- desenvolvimento de projetos.

Uma fórmula comum de fluxo de caixa livre é:

```text
Fluxo de caixa livre =
Fluxo de caixa operacional - Capex
```

---

## Cash — Caixa

Recursos monetários disponíveis ou equivalentes de alta liquidez.

**Campo comum:** `cash`

Pode incluir:

- dinheiro em caixa;
- depósitos bancários;
- equivalentes de caixa;
- aplicações de curtíssimo prazo, conforme a metodologia contábil.

---

## Cash and Cash Equivalents — Caixa e Equivalentes de Caixa

Ativos financeiros de altíssima liquidez, facilmente conversíveis em dinheiro e sujeitos a baixo risco de alteração de valor.

Normalmente incluem caixa disponível e aplicações com vencimento muito curto.

---

## Cash per Share — Caixa por Ação

Parcela do caixa total atribuída, de forma contábil, a cada ação.

**Campo comum:** `totalCashPerShare`

**Fórmula:**

```text
Caixa por ação =
Caixa total ÷ Número de ações
```

Não representa distribuição automática aos acionistas.

---

## Changed Symbol — Símbolo Alterado

Indicador que informa se a API precisou corrigir, substituir ou normalizar o ticker solicitado.

**Campo comum:** `changed`

Interpretação:

```text
false = ticker mantido
true = ticker alterado ou normalizado
```

---

## Common Shareholders — Acionistas Comuns

Acionistas titulares das ações às quais determinado lucro ou patrimônio é atribuído pela metodologia da fonte.

O termo pode ser utilizado em indicadores como `netIncomeToCommon`.

Em APIs internacionais, “common” pode se referir a ações ordinárias ou ao lucro disponível aos acionistas após ajustes específicos. A interpretação deve considerar a estrutura de classes de ações da companhia.

---

## Cost of Revenue — Custo da Receita

Custos diretamente relacionados à geração da receita.

**Campo comum:** `costOfRevenue`

Pode incluir:

- produção;
- extração;
- refino;
- matéria-prima;
- transporte;
- processamento;
- aquisição de produtos;
- outros custos diretamente associados às vendas.

**Fórmula:**

```text
Lucro bruto =
Receita total - Custo da receita
```

Quando o campo já vem negativo:

```text
Lucro bruto =
Receita total + Custo da receita
```

---

## Current Assets — Ativos Circulantes

Bens e direitos que a empresa espera realizar, consumir ou converter em caixa no curto prazo ou dentro do ciclo operacional.

**Campo comum:** `totalCurrentAssets`

Pode incluir:

- caixa;
- aplicações financeiras;
- contas a receber;
- estoques;
- tributos a recuperar;
- despesas antecipadas;
- outros créditos de curto prazo.

---

## Current Ratio — Liquidez Corrente

Indicador que compara os ativos circulantes com os passivos circulantes.

**Campo comum:** `currentRatio`

**Fórmula:**

```text
Liquidez corrente =
Ativo circulante ÷ Passivo circulante
```

Interpretação simplificada:

```text
Resultado = 1
R$ 1,00 de ativo circulante para cada R$ 1,00 de obrigação de curto prazo
```

Um valor abaixo de 1 exige análise adicional, mas não representa automaticamente incapacidade de pagamento.

---

## Currency — Moeda

Moeda utilizada nos valores monetários retornados pela API.

**Campo comum:** `currency`

Exemplo:

```text
BRL = real brasileiro
USD = dólar dos Estados Unidos
```

---

## Data Freshness — Frescor dos Dados

Avaliação da atualidade dos dados em relação à data da consulta e ao período de referência.

Deve considerar:

- horário da última cotação;
- data do último balanço;
- data do último trimestre;
- atraso da fonte;
- periodicidade esperada;
- data prevista para nova divulgação.

---

## Data Quality — Qualidade dos Dados

Avaliação da confiabilidade e adequação dos dados usados pelo sistema.

Deve considerar:

- completude;
- consistência matemática;
- fonte;
- período contábil;
- data e hora de referência;
- moeda;
- unidade;
- rastreabilidade;
- sinais positivos e negativos;
- metodologia;
- duplicidade de campos;
- divergência entre endpoints.

---

## Debt — Dívida Financeira

Obrigações financeiras da empresa relacionadas a empréstimos, financiamentos, títulos, debêntures, arrendamentos e outras formas de captação.

Não deve ser confundida com passivos totais.

---

## Debt to Equity — Dívida sobre Patrimônio Líquido

Indicador de alavancagem que compara a dívida com o patrimônio líquido.

**Campo comum:** `debtToEquity`

**Fórmula:**

```text
Dívida sobre patrimônio =
Dívida total ÷ Patrimônio líquido
```

Para exibição percentual:

```text
Percentual = índice × 100
```

Um resultado de `1,52` significa aproximadamente R$ 1,52 de dívida para cada R$ 1,00 de patrimônio líquido.

A fonte pode retornar esse indicador como razão decimal ou já como percentual. A documentação deve ser validada.

---

## Depreciation and Amortization — Depreciação e Amortização

Reconhecimento contábil da perda de valor ou consumo econômico de ativos ao longo do tempo.

- **Depreciação:** normalmente aplicada a ativos físicos.
- **Amortização:** normalmente aplicada a ativos intangíveis.

São despesas contábeis que podem não representar saída imediata de caixa.

---

## Dividend — Dividendo

Parcela do lucro distribuída aos acionistas.

Pode ser paga em dinheiro, ações ou outras formas permitidas.

Dividendos passados não garantem distribuições futuras.

---

## Dividend Date — Data do Dividendo

Data associada a um evento de provento.

**Campo comum:** `lastDividendDate`

Dependendo da API, pode representar:

- data de declaração;
- data de registro;
- data ex-dividendo;
- data de pagamento.

A documentação da fonte deve definir qual data está sendo retornada.

---

## Dividend Yield — Rendimento de Dividendos

Relação entre os proventos por ação e o preço da ação.

**Campos comuns:** `dividendYield` e `yield`

**Fórmula:**

```text
Dividend Yield =
Proventos por ação no período
÷ Preço da ação
```

Para exibição:

```text
Percentual = valor decimal × 100
```

A análise deve identificar se o indicador é:

- histórico;
- dos últimos 12 meses;
- anualizado;
- projetado;
- baseado apenas no último pagamento.

---

## Earnings Before Interest and Taxes — Lucro Antes de Juros e Impostos

Indicador conhecido como **EBIT**.

**Campo comum:** `ebit`

**Fórmula simplificada:**

```text
EBIT =
Receitas operacionais
- Custos operacionais
- Despesas operacionais
```

Em alguns demonstrativos, o EBIT pode coincidir com o lucro operacional. Em outros, pode haver diferenças de classificação.

---

## Earnings Before Interest, Taxes, Depreciation and Amortization — Lucro Antes de Juros, Impostos, Depreciação e Amortização

Indicador conhecido como **EBITDA**.

**Campo comum:** `ebitda`

**Fórmula simplificada:**

```text
EBITDA =
EBIT
+ Depreciação
+ Amortização
```

Ou:

```text
EBITDA =
Lucro operacional
+ Depreciação
+ Amortização
```

É uma medida de desempenho operacional, mas não equivale ao fluxo de caixa efetivamente disponível.

---

## EBITDA Margin — Margem EBITDA

Percentual da receita convertido em EBITDA.

**Campo comum:** `ebitdaMargins`

**Fórmula:**

```text
Margem EBITDA =
EBITDA ÷ Receita total
```

Para exibição:

```text
Percentual = resultado × 100
```

---

## Earnings Growth — Crescimento do Lucro

Variação percentual do lucro em relação ao período comparável.

**Campo comum:** `earningsGrowth`

**Fórmula:**

```text
Crescimento do lucro =
(Lucro atual - Lucro comparável)
÷ Lucro comparável
```

A comparação pode ser:

- trimestre contra trimestre anterior;
- trimestre contra o mesmo trimestre do ano anterior;
- período acumulado contra período acumulado comparável.

A metodologia precisa ser identificada na documentação da API.

---

## Earnings per Share — Lucro por Ação

Lucro atribuível a cada ação.

**Campos comuns:** `earningsPerShare`, `trailingEps` ou LPA.

**Fórmula:**

```text
Lucro por ação =
Lucro líquido atribuível aos acionistas
÷ Média ponderada de ações
```

Pode ser calculado com base:

- no último trimestre;
- no exercício anual;
- nos últimos 12 meses;
- em estimativas futuras.

---

## Educational Insight — Insight Educacional

Explicação analítica que ajuda o usuário a compreender dados, premissas, riscos e relações entre indicadores.

Não deve determinar conduta individualizada nem emitir comandos como:

- comprar;
- vender;
- aumentar posição;
- executar stop;
- sair da tese.

---

## End Date — Data de Encerramento

Data final do período contábil representado.

**Campo comum:** `endDate`

Exemplo:

```text
2025-12-31
```

Em balanços patrimoniais, representa a data da fotografia dos saldos. Em DREs e DFCs, representa o encerramento do período acumulado.

---

## Enterprise Value — Valor da Firma

Estimativa do valor econômico da operação da empresa considerando o valor de mercado e sua estrutura financeira.

**Campo comum:** `enterpriseValue`

**Fórmula simplificada:**

```text
Enterprise Value =
Valor de mercado
+ Dívida total
- Caixa e equivalentes
```

Uma versão mais completa pode incluir:

```text
Enterprise Value =
Valor de mercado
+ Dívida
+ Participações minoritárias
+ Ações preferenciais tratadas como financiamento
- Caixa
- Ativos financeiros não operacionais
```

O Enterprise Value não representa necessariamente o preço exato de aquisição da companhia.

---

## Enterprise Value to EBITDA — Valor da Firma sobre EBITDA

Múltiplo que compara o Enterprise Value com o EBITDA.

**Campo comum:** `enterpriseToEbitda`

**Fórmula:**

```text
EV/EBITDA =
Enterprise Value ÷ EBITDA
```

É útil para comparar empresas do mesmo setor porque considera valor de mercado, dívida e caixa.

Deve ser analisado junto com:

- crescimento;
- qualidade do EBITDA;
- necessidade de Capex;
- ciclo do setor;
- risco operacional;
- estrutura da dívida.

---

## Enterprise Value to Revenue — Valor da Firma sobre Receita

Múltiplo que compara o Enterprise Value com a receita.

**Campo comum:** `enterpriseToRevenue`

**Fórmula:**

```text
EV/Receita =
Enterprise Value ÷ Receita total
```

Não considera diferenças de margem. Empresas com a mesma receita podem ter lucratividade muito diferente.

---

## Ex-Dividend Date — Data Ex-Dividendo

Data a partir da qual a compra da ação não dá direito ao provento previamente anunciado.

Quem compra o ativo na data ex ou depois dela normalmente não recebe aquele provento específico.

---

## Final Cash Balance — Saldo Final de Caixa

Saldo de caixa e equivalentes ao final do período.

**Campo comum:** `finalCashBalance`

**Fórmula:**

```text
Saldo final de caixa =
Saldo inicial de caixa
+ Aumento ou redução líquida do caixa
```

Esse valor deve ser reconciliável com o caixa apresentado no balanço patrimonial, considerando diferenças de escopo e classificação.

---

## Financing Cash Flow — Fluxo de Caixa de Financiamento

Fluxo de entradas e saídas relacionado à estrutura de capital e ao financiamento da empresa.

**Campo comum:** `financingCashFlow`

Pode incluir:

- contratação de dívidas;
- amortização de empréstimos;
- emissão de ações;
- recompra de ações;
- dividendos;
- juros sobre capital próprio;
- pagamentos de arrendamentos.

Um valor negativo indica que a empresa devolveu mais recursos a credores e acionistas do que captou no período.

---

## Float Shares — Ações em Livre Circulação

Quantidade estimada de ações disponíveis para negociação no mercado.

**Campo comum:** `floatShares`

Normalmente exclui participações de:

- controladores;
- governos;
- fundadores;
- investidores estratégicos;
- ações bloqueadas ou restritas.

**Fórmula de free float percentual:**

```text
Free float percentual =
Ações em livre circulação
÷ Ações totais em circulação
```

A metodologia pode considerar uma classe específica de ações ou todas as classes da empresa.

---

## Free Cash Flow — Fluxo de Caixa Livre

Caixa gerado pela operação após os investimentos necessários.

**Campos comuns:** `freeCashFlow` ou `freeCashflow`

**Fórmula mais comum:**

```text
Fluxo de caixa livre =
Fluxo de caixa operacional - Capex
```

Quando a API usa todo o fluxo de investimentos como aproximação:

```text
Fluxo de caixa livre =
Fluxo de caixa operacional
+ Fluxo de caixa de investimentos
```

Essa segunda fórmula pode ser imprecisa quando o fluxo de investimentos contém aquisições, vendas de ativos, aplicações ou resgates financeiros.

---

## Free Cash Flow Margin — Margem de Fluxo de Caixa Livre

Percentual da receita convertido em fluxo de caixa livre.

**Fórmula:**

```text
Margem de fluxo de caixa livre =
Fluxo de caixa livre
÷ Receita total
```

---

## Fundamental Data Freshness — Frescor Fundamentalista

Avaliação da atualidade dos dados contábeis e fundamentalistas.

Deve considerar:

- período do último demonstrativo;
- tipo de demonstrativo;
- data de publicação;
- data de referência;
- expectativa de divulgação do próximo resultado;
- atraso da fonte;
- uso de dados anuais, trimestrais ou dos últimos 12 meses.

---

## Gross Margin — Margem Bruta

Percentual da receita que permanece após a dedução dos custos diretamente relacionados à geração da receita.

**Campo comum:** `grossMargins`

**Fórmula:**

```text
Margem bruta =
Lucro bruto ÷ Receita total
```

---

## Gross Profit — Lucro Bruto

Resultado obtido após a dedução do custo da receita.

**Campos comuns:** `grossProfit` ou `grossProfits`

**Fórmula:**

```text
Lucro bruto =
Receita total - Custo da receita
```

---

## Income Before Tax — Lucro Antes dos Impostos

Resultado obtido antes da despesa de imposto de renda e contribuição social.

**Campo comum:** `incomeBeforeTax`

Pode incluir:

- resultado operacional;
- resultado financeiro;
- variações cambiais;
- equivalência patrimonial;
- outros ganhos e perdas não operacionais.

---

## Income from Operations — Resultado Proveniente das Operações

Campo utilizado em algumas APIs de fluxo de caixa para representar recursos ou resultado gerado pelas operações antes de certos ajustes.

**Campo comum:** `incomeFromOperations`

O significado pode variar conforme a fonte, podendo representar:

- resultado operacional ajustado;
- recursos gerados antes do capital de giro;
- lucro acrescido de itens não monetários;
- caixa operacional antes de determinados pagamentos.

A diferença em relação ao fluxo de caixa operacional deve ser analisada com base na documentação do provedor.

---

## Income Statement — Demonstração do Resultado do Exercício

Demonstração contábil conhecida como **DRE**.

Apresenta como a receita do período foi transformada em lucro ou prejuízo.

Estrutura simplificada:

```text
Receita
- Custo da receita
= Lucro bruto
- Despesas operacionais
= Lucro operacional
+/- Resultado financeiro e outros itens
= Lucro antes dos impostos
- Impostos
= Lucro líquido
```

---

## Income Tax Expense — Despesa com Imposto de Renda

Despesa tributária calculada sobre o resultado da empresa.

**Campo comum:** `incomeTaxExpense`

Pode incluir:

- imposto corrente;
- imposto diferido;
- contribuição social;
- ajustes tributários;
- diferenças temporárias.

**Taxa efetiva:**

```text
Taxa efetiva de imposto =
Despesa com imposto
÷ Lucro antes dos impostos
```

Se a despesa vier negativa, use o valor absoluto para a taxa:

```text
Taxa efetiva =
|Despesa com imposto|
÷ Lucro antes dos impostos
```

---

## Increase or Decrease in Cash — Aumento ou Redução do Caixa

Variação líquida do saldo de caixa durante o período.

**Campo comum:** `increaseOrDecreaseInCash`

**Fórmula básica:**

```text
Variação do caixa =
Fluxo operacional
+ Fluxo de investimentos
+ Fluxo de financiamento
+ Outros efeitos
```

Outros efeitos podem incluir:

- variação cambial;
- conversão de demonstrações;
- operações descontinuadas;
- reclassificações;
- mudanças no perímetro de consolidação.

---

## Informational Price Threshold — Limiar Informativo de Preço

Valor definido para gerar um alerta factual quando o preço do ativo o alcançar ou cruzar.

O limiar não deve, por si só, ser interpretado como recomendação de negociação.

---

## Initial Cash Balance — Saldo Inicial de Caixa

Saldo de caixa e equivalentes no início do período.

**Campo comum:** `initialCashBalance`

Normalmente corresponde ao saldo final do período anterior, sujeito a reclassificações e ajustes de consolidação.

---

## Intangible Assets — Ativos Intangíveis

Ativos sem forma física que possuem valor econômico.

**Campo comum:** `intangibleAssets`

Exemplos:

- softwares;
- licenças;
- patentes;
- marcas;
- concessões;
- direitos de exploração;
- contratos;
- goodwill, conforme a classificação da fonte.

---

## Investment Cash Flow — Fluxo de Caixa de Investimentos

Entradas e saídas de caixa relacionadas à aquisição ou venda de ativos e investimentos.

**Campo comum:** `investmentCashFlow`

Pode incluir:

- Capex;
- compra de equipamentos;
- construção de instalações;
- aquisição de empresas;
- compra ou venda de participações;
- venda de ativos;
- aplicações financeiras;
- resgates financeiros.

Um valor negativo indica que houve mais saídas do que entradas nessa categoria.

---

## Last Dividend Date — Data do Último Dividendo

Data associada ao último evento de dividendo reconhecido pela fonte.

**Campo comum:** `lastDividendDate`

É necessário confirmar se representa:

- data ex;
- data de pagamento;
- data de aprovação;
- data de registro.

---

## Liability — Passivo

Obrigação presente da empresa decorrente de eventos passados.

Exemplos:

- fornecedores;
- empréstimos;
- impostos;
- provisões;
- salários;
- dividendos a pagar;
- arrendamentos;
- obrigações de curto e longo prazo.

---

## Long Name — Nome Completo do Ativo

Nome mais descritivo da empresa, fundo ou ativo.

**Campo comum:** `longName`

Pode incluir abreviações sobre a classe da ação.

Exemplo:

```text
Pfd = Preferred = Preferencial
```

---

## Lower User-Defined Price Threshold — Limiar Inferior de Preço Definido pelo Usuário

Valor configurado pelo usuário para gerar alerta quando o preço atingir ou cruzar o limite inferior.

Deve ser tratado como parâmetro de observação, não como ordem automática ou recomendação de compra.

---

## Market Capitalization — Valor de Mercado

Valor agregado das ações da empresa ao preço de mercado.

**Campo comum:** `marketCap`

**Fórmula:**

```text
Valor de mercado =
Preço da ação
× Quantidade de ações considerada
```

Em empresas com mais de uma classe de ações, a fonte pode calcular o valor de mercado da companhia inteira.

---

## Market Price — Preço de Mercado

Preço pelo qual o ativo está sendo negociado.

**Campo comum:** `regularMarketPrice`

Normalmente representa o último preço conhecido no mercado regular.

---

## Margin of Safety — Margem de Segurança

Diferença entre o preço atual e uma referência calculada por um modelo de estudo.

**Fórmula possível:**

```text
Margem de segurança =
(Preço de referência - Preço atual)
÷ Preço de referência
```

A margem deve ser apresentada com:

- premissas;
- fonte dos dados;
- método de cálculo;
- limitações;
- data de referência;
- sensibilidade a cenários.

Não representa garantia de proteção contra perdas.

---

## Most Recent Quarter — Trimestre Mais Recente

Data de encerramento do trimestre mais recente utilizado pela fonte.

**Campo comum:** `mostRecentQuarter`

Exemplo:

```text
2026-03-31
```

Pode servir como referência para indicadores de balanço, crescimento ou rentabilidade.

---

## Net Debt — Dívida Líquida

Dívida financeira após a dedução de caixa e equivalentes.

**Fórmula simplificada:**

```text
Dívida líquida =
Dívida total - Caixa total
```

Uma fórmula mais detalhada pode deduzir também investimentos de curto prazo.

---

## Net Income — Lucro Líquido

Resultado final da empresa após custos, despesas, resultado financeiro, impostos e demais efeitos reconhecidos.

**Campo comum:** `netIncome`

**Fórmula simplificada:**

```text
Lucro líquido =
Lucro antes dos impostos
- Impostos
```

Ou, de forma ampla:

```text
Lucro líquido =
Receitas
- Custos
- Despesas
+/- Resultado financeiro
+/- Outros resultados
- Impostos
```

---

## Net Income to Common — Lucro Líquido Atribuível aos Acionistas Comuns

Parcela do lucro líquido disponível aos acionistas considerados “comuns” pela metodologia da fonte.

**Campo comum:** `netIncomeToCommon`

Pode ser utilizado no cálculo do lucro por ação.

---

## Net Margin — Margem Líquida

Percentual da receita convertido em lucro líquido.

**Campos comuns:** `profitMargins`

**Fórmula:**

```text
Margem líquida =
Lucro líquido ÷ Receita total
```

---

## Observation Range of the Study — Faixa de Observação do Estudo

Intervalo de preço utilizado para acompanhar o comportamento do ativo dentro de um modelo de estudo.

A faixa pode ser construída com base em:

- cenários;
- premissas;
- preço de referência;
- margem de segurança;
- volatilidade;
- limiares definidos pelo usuário.

Não deve ser apresentada como faixa garantida de negociação.

---

## Operating Cash Flow — Fluxo de Caixa Operacional

Caixa gerado ou consumido pelas atividades principais da empresa.

**Campos comuns:** `operatingCashFlow` ou `operatingCashflow`

Pode considerar:

- recebimentos de clientes;
- pagamentos a fornecedores;
- pagamentos a funcionários;
- impostos;
- capital de giro;
- ajustes de itens sem efeito caixa.

Uma medida de conversão do lucro em caixa é:

```text
Conversão do lucro em caixa =
Fluxo de caixa operacional
÷ Lucro líquido
```

---

## Operating Expenses — Despesas Operacionais

Despesas necessárias para a manutenção das atividades da empresa que não fazem parte diretamente do custo da receita.

**Campo comum:** `totalOperatingExpenses`

Podem incluir:

- despesas administrativas;
- despesas comerciais;
- pesquisa e desenvolvimento;
- remuneração administrativa;
- provisões;
- depreciação e amortização;
- outras despesas operacionais.

A classificação exata varia conforme a fonte.

---

## Operating Income — Lucro Operacional

Resultado gerado pelas atividades operacionais antes de resultado financeiro e impostos.

**Campo comum:** `operatingIncome`

**Fórmula:**

```text
Lucro operacional =
Lucro bruto - Despesas operacionais
```

Quando despesas já vêm negativas:

```text
Lucro operacional =
Lucro bruto + Despesas operacionais
```

---

## Operating Margin — Margem Operacional

Percentual da receita convertido em lucro operacional.

**Campo comum:** `operatingMargins`

**Fórmula:**

```text
Margem operacional =
Lucro operacional ÷ Receita total
```

---

## PEG Ratio — Índice Preço/Lucro sobre Crescimento

Indicador que relaciona o múltiplo P/L com uma taxa de crescimento dos lucros.

**Campo comum:** `pegRatio`

**Fórmula simplificada:**

```text
PEG =
P/L ÷ Taxa de crescimento dos lucros
```

A escala da taxa de crescimento precisa ser conhecida:

```text
Crescimento de 20%
pode ser usado como 20 ou 0,20,
dependendo da metodologia da fonte
```

O PEG é sensível a estimativas de crescimento e pode ser pouco confiável para empresas cíclicas.

---

## Price Change — Variação Absoluta do Preço

Diferença monetária entre o preço atual e o fechamento anterior.

**Campo comum:** `regularMarketChange`

**Fórmula:**

```text
Variação absoluta =
Preço atual - Fechamento anterior
```

---

## Price Change Percent — Variação Percentual do Preço

Variação percentual do preço em relação ao fechamento anterior.

**Campo comum:** `regularMarketChangePercent`

**Fórmula:**

```text
Variação percentual =
(Preço atual - Fechamento anterior)
÷ Fechamento anterior
× 100
```

---

## Price Range — Faixa de Preço

Intervalo entre dois preços de referência.

Pode representar:

- mínima e máxima do dia;
- mínima e máxima de 52 semanas;
- faixa de observação de um estudo.

---

## Price-to-Book Ratio — Preço sobre Valor Patrimonial

Indicador conhecido como **P/VP**.

**Campo comum:** `priceToBook`

**Fórmula:**

```text
P/VP =
Preço da ação
÷ Valor patrimonial por ação
```

Interpretação simplificada:

```text
P/VP = 1
Preço próximo ao valor patrimonial por ação

P/VP > 1
Preço acima do valor patrimonial por ação

P/VP < 1
Preço abaixo do valor patrimonial por ação
```

Um P/VP baixo não significa automaticamente que o ativo está barato.

---

## Price-to-Earnings Ratio — Preço sobre Lucro

Indicador conhecido como **P/L**.

**Campo comum:** `trailingPE`

**Fórmula:**

```text
P/L =
Preço da ação
÷ Lucro por ação
```

Também pode ser calculado como:

```text
P/L =
Valor de mercado
÷ Lucro líquido
```

Um P/L baixo pode refletir preço reduzido, lucro temporariamente elevado, risco maior ou expectativa de queda futura nos resultados.

---

## Property, Plant and Equipment — Imobilizado

Ativos físicos utilizados na operação da empresa.

**Campo comum:** `propertyPlantEquipment`

Exemplos:

- terrenos;
- edifícios;
- máquinas;
- plataformas;
- refinarias;
- navios;
- oleodutos;
- gasodutos;
- equipamentos industriais.

Também é conhecido como **PPE** ou **ativo imobilizado**.

---

## Quarterly Earnings Growth — Crescimento Trimestral do Lucro

Variação percentual do lucro do trimestre em relação a um período comparável.

**Campo comum:** `earningsQuarterlyGrowth`

**Fórmula:**

```text
Crescimento trimestral do lucro =
(Lucro do trimestre atual - Lucro do trimestre comparável)
÷ Lucro do trimestre comparável
```

É necessário verificar se a comparação é:

- contra o trimestre imediatamente anterior;
- contra o mesmo trimestre do ano anterior.

---

## Quarterly Data — Dados Trimestrais

Informações contábeis referentes a um trimestre.

**Representação comum:**

```text
type = "quarterly"
```

São úteis para acompanhar mudanças mais recentes, mas podem sofrer efeitos sazonais.

---

## Quick Ratio — Liquidez Seca

Indicador de liquidez de curto prazo que exclui ativos menos líquidos, especialmente estoques.

**Campo comum:** `quickRatio`

**Fórmula simplificada:**

```text
Liquidez seca =
(Ativo circulante - Estoques)
÷ Passivo circulante
```

Algumas metodologias utilizam apenas caixa, aplicações e contas a receber.

---

## Reference Price of the Study — Preço de Referência do Estudo

Valor calculado por um modelo para comparação educacional com o preço de mercado.

Pode considerar:

- fluxo de caixa descontado;
- múltiplos;
- dividendos;
- patrimônio;
- cenários;
- premissas operacionais;
- margem de segurança.

Não deve ser apresentado como preço garantido ou objetivo individualizado de negociação.

---

## Regular Market — Mercado Regular

Sessão normal de negociação do ativo, excluindo, quando aplicável:

- pré-mercado;
- pós-mercado;
- leilões especiais;
- negociações fora do horário regular.

---

## Regular Market Day High — Máxima do Dia

Maior preço negociado durante o pregão regular.

**Campo comum:** `regularMarketDayHigh`

---

## Regular Market Day Low — Mínima do Dia

Menor preço negociado durante o pregão regular.

**Campo comum:** `regularMarketDayLow`

---

## Regular Market Day Range — Faixa do Dia

Intervalo entre a mínima e a máxima do pregão regular.

**Campo comum:** `regularMarketDayRange`

**Representação:**

```text
Mínima do dia - Máxima do dia
```

---

## Regular Market Open — Preço de Abertura

Preço da primeira negociação ou preço oficial de abertura no mercado regular.

**Campo comum:** `regularMarketOpen`

---

## Regular Market Previous Close — Fechamento Anterior

Preço de fechamento do pregão regular anterior.

**Campo comum:** `regularMarketPreviousClose`

É utilizado como referência para calcular a variação diária.

---

## Regular Market Price — Preço Atual do Mercado Regular

Último preço conhecido do ativo durante o mercado regular.

**Campo comum:** `regularMarketPrice`

Dependendo da fonte, pode ser um preço em tempo real, atrasado ou o último preço disponível.

---

## Regular Market Time — Horário do Mercado Regular

Data e hora associadas ao preço de mercado retornado.

**Campo comum:** `regularMarketTime`

Normalmente é informado no padrão ISO 8601.

Exemplo:

```text
2026-07-07T13:15:30.000Z
```

O sufixo `Z` indica horário UTC.

---

## Regular Market Volume — Volume do Mercado Regular

Quantidade de ações ou unidades negociadas durante o pregão regular.

**Campo comum:** `regularMarketVolume`

Representa quantidade de papéis, não volume financeiro.

**Estimativa simplificada de volume financeiro:**

```text
Volume financeiro aproximado =
Volume negociado × Preço médio
```

Usar o preço atual em vez do preço médio gera apenas uma aproximação.

---

## Requested At — Data e Hora da Requisição

Data e hora em que a consulta foi realizada ou processada pela API.

**Campo comum:** `requestedAt`

Normalmente utiliza formato ISO 8601.

---

## Requested Symbol — Símbolo Solicitado

Ticker originalmente enviado à API.

**Campo comum:** `requestedSymbol`

Serve para rastrear o valor informado pelo cliente antes de qualquer normalização.

---

## Research Model — Modelo de Estudo

Conjunto versionado de critérios, premissas, fórmulas e regras utilizado para analisar um ativo de forma auditável.

Deve definir:

- objetivo;
- universo de ativos;
- indicadores utilizados;
- pesos;
- filtros;
- períodos;
- tratamento de dados ausentes;
- fontes;
- limitações;
- critérios de atualização.

---

## Research Model Under Monitoring — Modelo de Estudo Acompanhado

Modelo associado a um ativo acompanhado para observação histórica.

Pode ser utilizado para:

- recalcular indicadores;
- acompanhar alterações de premissas;
- gerar alertas informativos;
- registrar versões;
- avaliar aderência;
- identificar pontos de reavaliação.

---

## Return on Assets — Retorno sobre Ativos

Indicador conhecido como **ROA**.

**Campo comum:** `returnOnAssets`

**Fórmula:**

```text
ROA =
Lucro líquido
÷ Ativos médios
```

Uma versão simplificada pode usar ativos no final do período:

```text
ROA aproximado =
Lucro líquido
÷ Ativos totais
```

Mede a eficiência da empresa na geração de lucro a partir de seus ativos.

---

## Return on Equity — Retorno sobre o Patrimônio Líquido

Indicador conhecido como **ROE**.

**Campo comum:** `returnOnEquity`

**Fórmula:**

```text
ROE =
Lucro líquido
÷ Patrimônio líquido médio
```

Uma versão simplificada pode utilizar o patrimônio no final do período.

Um ROE elevado pode resultar de boa rentabilidade, maior alavancagem ou ambos.

---

## Revenue — Receita

Valor obtido pela empresa com vendas de produtos e prestação de serviços antes da dedução de custos e despesas.

---

## Revenue Growth — Crescimento da Receita

Variação percentual da receita em relação ao período comparável.

**Campo comum:** `revenueGrowth`

**Fórmula:**

```text
Crescimento da receita =
(Receita atual - Receita comparável)
÷ Receita comparável
```

---

## Score of Adherence — Score de Aderência

Nota que expressa o quanto um ativo atende aos critérios definidos em um modelo de estudo.

O cálculo deve ser:

- explicável;
- reproduzível;
- versionado;
- auditável;
- resistente a dados ausentes;
- acompanhado das premissas.

Exemplo de fórmula ponderada:

```text
Score =
Σ (Nota do critério × Peso do critério)
÷ Σ Pesos
```

O score não deve ser tratado isoladamente como recomendação de investimento.

---

## Screener — Filtro de Ativos

Ferramenta que permite filtrar, classificar e ordenar ativos com base em critérios escolhidos.

Pode utilizar:

- valuation;
- rentabilidade;
- crescimento;
- liquidez;
- endividamento;
- margens;
- dividendos;
- qualidade dos dados;
- aderência a modelos.

O screener apresenta dados e critérios; não precisa emitir recomendação individualizada.

---

## Shares Outstanding — Ações Emitidas em Circulação

Quantidade total de ações emitidas e consideradas em circulação pela metodologia da fonte.

**Campo comum:** `sharesOutstanding`

Pode incluir diferentes classes de ações.

É utilizado em cálculos como:

```text
Valor de mercado =
Preço médio considerado
× Ações em circulação
```

e:

```text
Lucro por ação =
Lucro atribuível
÷ Média ponderada de ações
```

---

## Short Name — Nome Curto do Ativo

Nome resumido utilizado para identificação do ativo.

**Campo comum:** `shortName`

Pode coincidir com o ticker.

---

## Short-Term Investments — Investimentos de Curto Prazo

Aplicações financeiras com expectativa de realização em curto prazo.

**Campo comum:** `shortTermInvestments`

Podem incluir:

- títulos;
- certificados;
- fundos;
- depósitos;
- outros ativos financeiros negociáveis.

Dependendo da metodologia, podem ser somados ao caixa na análise de liquidez.

---

## Study Reassessment Point — Ponto de Reavaliação

Condição que indica necessidade de revisar um modelo de estudo.

Pode ser acionado por:

- alteração de premissa;
- novo resultado trimestral;
- deterioração de indicador;
- mudança relevante de preço;
- aumento de endividamento;
- queda de margem;
- inconsistência de dados;
- evento corporativo.

Indica necessidade de nova análise, sem determinar uma conduta de negociação.

---

## Symbol — Símbolo do Ativo

Ticker efetivamente reconhecido e retornado pela API.

**Campo comum:** `symbol`

Pode ser diferente de `requestedSymbol` quando a fonte aplica normalização ou substituição.

---

## Tax Rate — Alíquota Efetiva de Imposto

Percentual do lucro antes dos impostos consumido por tributos sobre o resultado.

**Fórmula:**

```text
Alíquota efetiva =
Despesa com impostos
÷ Lucro antes dos impostos
```

Quando a despesa vem negativa:

```text
Alíquota efetiva =
|Despesa com impostos|
÷ Lucro antes dos impostos
```

---

## Total Assets — Ativos Totais

Soma de todos os ativos reconhecidos no balanço patrimonial.

**Campo comum:** `totalAssets`

**Estrutura simplificada:**

```text
Ativos totais =
Ativos circulantes
+ Imobilizado
+ Intangíveis
+ Investimentos
+ Outros ativos não circulantes
```

---

## Total Cash — Caixa Total

Soma de caixa, equivalentes e, conforme a fonte, investimentos financeiros de alta liquidez.

**Campo comum:** `totalCash`

Pode divergir do campo `cash` do balanço em razão de:

- data de referência;
- inclusão de investimentos de curto prazo;
- metodologia;
- arredondamentos;
- conversão cambial.

---

## Total Debt — Dívida Total

Total de obrigações financeiras reconhecidas pela fonte.

**Campo comum:** `totalDebt`

Pode incluir:

- empréstimos;
- financiamentos;
- debêntures;
- títulos;
- arrendamentos;
- dívida de curto prazo;
- dívida de longo prazo.

Não deve ser confundida com `totalLiab`.

---

## Total Liabilities — Passivos Totais

Soma de todas as obrigações da empresa com terceiros.

**Campo comum:** `totalLiab`

Pode incluir:

- dívida financeira;
- fornecedores;
- impostos;
- provisões;
- obrigações trabalhistas;
- arrendamentos;
- outros passivos.

**Relação contábil:**

```text
Patrimônio líquido =
Ativos totais - Passivos totais
```

---

## Total Operating Expenses — Despesas Operacionais Totais

Soma das despesas operacionais reconhecidas no período.

**Campo comum:** `totalOperatingExpenses`

**Fórmula:**

```text
Lucro operacional =
Lucro bruto - Despesas operacionais totais
```

Quando a API retorna despesas negativas:

```text
Lucro operacional =
Lucro bruto + Despesas operacionais totais
```

---

## Total Revenue — Receita Total

Receita acumulada no período.

**Campo comum:** `totalRevenue`

Pode representar:

- trimestre;
- exercício anual;
- últimos 12 meses.

O período deve ser identificado pela data e pelo tipo do demonstrativo.

---

## Took — Tempo de Processamento

Tempo consumido pela API para processar a requisição.

**Campo comum:** `took`

A unidade pode ser:

- milissegundos;
- microssegundos;
- outra unidade definida pelo provedor.

Não se deve assumir a unidade sem consultar a documentação da API.

---

## Trailing Earnings per Share — Lucro por Ação dos Últimos 12 Meses

Lucro por ação calculado com base nos últimos 12 meses.

**Campo comum:** `trailingEps`

**Fórmula:**

```text
Trailing EPS =
Lucro líquido atribuível dos últimos 12 meses
÷ Média ponderada de ações
```

---

## Trailing Price-to-Earnings Ratio — Preço sobre Lucro dos Últimos 12 Meses

P/L calculado com base no lucro acumulado dos últimos 12 meses.

**Campo comum:** `trailingPE`

**Fórmula:**

```text
Trailing P/L =
Preço atual
÷ Lucro por ação dos últimos 12 meses
```

---

## Upper User-Defined Price Threshold — Limiar Superior de Preço Definido pelo Usuário

Valor configurado para gerar alerta quando o preço atingir ou cruzar o limite superior.

Deve ser tratado como parâmetro de observação, não como ordem automática ou recomendação de venda.

---

## Valuation — Avaliação de Valor

Processo de estimar o valor econômico de uma empresa ou ativo.

Pode utilizar:

- fluxo de caixa descontado;
- múltiplos;
- dividendos;
- patrimônio;
- valor de liquidação;
- cenários;
- análise de sensibilidade.

Valuation é uma estimativa dependente de premissas, não um valor exato.

---

## Volume — Volume Negociado

Quantidade de ações ou unidades negociadas em determinado período.

**Campo comum:** `regularMarketVolume`

Não deve ser confundido com volume financeiro.

---

## Volume Financeiro — Financial Trading Volume

Valor monetário movimentado nas negociações.

**Fórmula exata aproximável:**

```text
Volume financeiro =
Σ (Quantidade negociada em cada negócio × Preço de cada negócio)
```

Estimativa simplificada:

```text
Volume financeiro aproximado =
Volume de ações × Preço médio
```

---

## Yield — Rendimento

Termo genérico para rendimento de um ativo.

No payload discutido, o campo `yield` foi utilizado como equivalente a `dividendYield`.

**Fórmula no contexto de dividendos:**

```text
Yield =
Proventos por ação
÷ Preço da ação
```

A semântica exata deve ser confirmada na documentação da API.

---

# Demonstrativos Financeiros

## Balance Sheet — Balanço Patrimonial

Apresenta os saldos de ativos, passivos e patrimônio líquido em uma data.

```text
Ativos = Passivos + Patrimônio líquido
```

## Income Statement — Demonstração do Resultado do Exercício

Apresenta receitas, custos, despesas, impostos e lucro durante um período.

```text
Receita
- Custos
- Despesas
+/- Outros resultados
- Impostos
= Lucro líquido
```

## Cash Flow Statement — Demonstração dos Fluxos de Caixa

Apresenta entradas e saídas de caixa durante um período.

```text
Variação do caixa =
Fluxo operacional
+ Fluxo de investimentos
+ Fluxo de financiamento
+ Outros efeitos
```

---

# Fórmulas Consolidadas

## Capitalização de Mercado

```text
Valor de mercado =
Preço da ação × Quantidade de ações considerada
```

## Enterprise Value

```text
Enterprise Value =
Valor de mercado + Dívida total - Caixa
```

## Dívida Líquida

```text
Dívida líquida =
Dívida total - Caixa total
```

## Lucro Bruto

```text
Lucro bruto =
Receita total - Custo da receita
```

## Lucro Operacional

```text
Lucro operacional =
Lucro bruto - Despesas operacionais
```

## Lucro Líquido

```text
Lucro líquido =
Lucro antes dos impostos - Impostos
```

## Margem Bruta

```text
Margem bruta =
Lucro bruto ÷ Receita total
```

## Margem EBITDA

```text
Margem EBITDA =
EBITDA ÷ Receita total
```

## Margem Operacional

```text
Margem operacional =
Lucro operacional ÷ Receita total
```

## Margem Líquida

```text
Margem líquida =
Lucro líquido ÷ Receita total
```

## Fluxo de Caixa Livre

```text
Fluxo de caixa livre =
Fluxo de caixa operacional - Capex
```

## Margem de Fluxo de Caixa Livre

```text
Margem de fluxo de caixa livre =
Fluxo de caixa livre ÷ Receita total
```

## Liquidez Corrente

```text
Liquidez corrente =
Ativo circulante ÷ Passivo circulante
```

## Liquidez Seca

```text
Liquidez seca =
(Ativo circulante - Estoques)
÷ Passivo circulante
```

## Dívida sobre Patrimônio

```text
Dívida sobre patrimônio =
Dívida total ÷ Patrimônio líquido
```

## ROA

```text
ROA =
Lucro líquido ÷ Ativos médios
```

## ROE

```text
ROE =
Lucro líquido ÷ Patrimônio líquido médio
```

## Lucro por Ação

```text
LPA =
Lucro líquido atribuível
÷ Média ponderada de ações
```

## P/L

```text
P/L =
Preço da ação ÷ Lucro por ação
```

## P/VP

```text
P/VP =
Preço da ação ÷ Valor patrimonial por ação
```

## PEG

```text
PEG =
P/L ÷ Taxa de crescimento dos lucros
```

## EV/Receita

```text
EV/Receita =
Enterprise Value ÷ Receita total
```

## EV/EBITDA

```text
EV/EBITDA =
Enterprise Value ÷ EBITDA
```

## Dividend Yield

```text
Dividend Yield =
Proventos por ação ÷ Preço da ação
```

## Crescimento

```text
Crescimento =
(Valor atual - Valor anterior)
÷ Valor anterior
```

## Variação Diária do Preço

```text
Variação absoluta =
Preço atual - Fechamento anterior
```

```text
Variação percentual =
(Preço atual - Fechamento anterior)
÷ Fechamento anterior
× 100
```

## Patrimônio Líquido Implícito

```text
Patrimônio líquido =
Ativos totais - Passivos totais
```

## Conversão do Lucro em Caixa

```text
Conversão do lucro em caixa =
Fluxo de caixa operacional ÷ Lucro líquido
```

## Variação do Caixa

```text
Variação do caixa =
Saldo final de caixa - Saldo inicial de caixa
```

ou:

```text
Variação do caixa =
Fluxo operacional
+ Fluxo de investimentos
+ Fluxo de financiamento
+ Outros efeitos
```
