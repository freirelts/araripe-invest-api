# Documentação técnica - Araripe Invest

Esta pasta documenta o Araripe Invest para pessoas técnicas e não técnicas. A proposta é explicar o sistema, a lógica financeira, os limites do produto e as integrações planejadas, servindo como referência para implementação e evolução do backend.

## Índice

1. [Visão geral do produto](01-visao-geral.md)
2. [Arquitetura e integrações](02-arquitetura-e-integracoes.md)
3. [Conceitos financeiros para leigos](03-conceitos-financeiros.md)
4. [Dados, fontes e qualidade](04-dados-fontes-e-qualidade.md)
5. [Motor de regras, modelos de estudo e scoring](05-motor-de-regras-setups-scoring.md)
6. [Gestão de risco](06-gestao-de-risco.md)
7. [Backtest e métricas](07-backtest-e-metricas.md)
8. [Modelo de dados](08-modelo-de-dados.md)
9. [APIs e telas](09-apis-e-telas.md)
10. [Operação, segurança e compliance](10-operacao-seguranca-compliance.md)
11. [Roadmap](11-roadmap.md)
12. [Glossário](12-glossario.md)
13. [Conteúdos de estudo na web](13-conteudos-estudo-web.md)
14. [Documentação brapi.dev usada pelo projeto](14-documentacao-brapi.md)
15. [Diagnóstico regulatório e plano de adequação](15-diagnostico-regulatorio-adequacao.md)

## Decisão central

O Araripe Invest é uma plataforma educacional, informativa e analítica para estudo e acompanhamento de ativos brasileiros. O produto não orienta compra, venda, manutenção, aumento, redução, alocação ou encerramento de posição, não executa ordens, não integra corretora e não faz suitability.

## Referências externas consultadas

- brapi.dev: API REST brasileira com dados financeiros, cotações, histórico OHLCV, dividendos, fundamentos, cripto, câmbio e indicadores econômicos: https://brapi.dev/ e https://brapi.dev/docs
- B3 Cotações Históricas: séries de preços desde 1986, sem ajuste por inflação ou proventos: https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/cotacoes-historicas/
- Banco Central SGS/BCData: interface JSON para séries temporais econômico-financeiras: https://api.bcb.gov.br/dados/serie/bcdata.sgs.{codigo_serie}/dados?formato=json
- Portal Dados Abertos CVM: conjuntos de dados de companhias abertas, fundos e documentos regulatórios: https://dados.cvm.gov.br/
- Resolução CVM 80: prazos de entrega de DFP e ITR usados como referência para frescor fundamentalista: https://conteudo.cvm.gov.br/export/sites/cvm/legislacao/resolucoes/anexos/001/resol080consolid.pdf
- OpenAI API: geração de texto e saídas estruturadas para enriquecimento contextual: https://developers.openai.com/api/docs
