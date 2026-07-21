# Roadmap

## Versão 1 - MVP

- Universo monitorado formado por ativos cadastrados e ativos no sistema.
- Coleta diária via brapi.dev.
- Candles OHLCV.
- Dados fundamentalistas: perfil, estatísticas, dados financeiros, balanço, DRE, fluxo de caixa e dividendos.
- Indicadores técnicos de longo prazo.
- Três modelos de estudo iniciais:
  - qualidade fundamentalista com preço razoável;
  - dividendos sustentáveis;
  - crescimento rentável com tendência longa saudável.
- Filtros eliminatórios.
- Score 0 a 100.
- Risco analítico e limiares informativos.
- Screener diário de modelos de estudo.
- Registros informativos de posição real declarados pelo usuário.
- Associação de modelo de estudo acompanhado a posição aberta do cliente.
- Alertas informativos diárias por posição.
- Análise econômica de modelo de estudo com OpenAI por recurso próprio de API.
- E-mail diário consolidado via Spring Mail com alertas informativos das posições declaradas.
- Interface web como canal principal e e-mail como resumo informativo.
- PostgreSQL.
- Frontend Angular.
- Login, cadastro de usuários e perfis `CUSTOMER` e `ADMIN`.
- Landing page pública com botão de login no canto superior direito.
- Dashboards com gráficos e componentes Material Design.
- Backtest mínimo de acompanhamento educacional de ativos.

## Versão 1.1 - Qualidade e operação

- Melhor diagnóstico de dados fundamentalistas.
- Melhor diagnóstico de alertas informativos por posição declarada.
- Painel de e-mails consolidados e seus itens.
- Painel de auditoria de IA, prompts, fontes e validações.
- Painel de jobs.
- Ajuste fino de filtros.
- Relatórios de falsos positivos.
- Exportação CSV.
- Métricas operacionais.
- Comparação básica contra benchmark.

## Versão 1.2 - Backtest ampliado

- Uso de séries históricas B3.
- Controle explícito de dados ajustados e não ajustados.
- Custos operacionais.
- Slippage.
- Dividendos reinvestidos.
- Entrada parcelada.
- Relatórios por modelo de estudo, setor e período.

## Versão 2 - Contexto macro e fundamentalista avançado

- Banco Central SGS como fonte complementar para Selic, CDI, IPCA e câmbio.
- CVM Dados Abertos para documentos e eventos.
- Filtros por setor.
- Janela de eventos corporativos.
- Métricas históricas por setor.
- Comparação de valuation contra pares.

## Versão 3 - Colaboração e preferências

- Preferências por usuário.
- Watchlist personalizada.
- Comentários e anotações do usuário.
- Evolução dos registros informativos de posição e watchlist personalizada.

## Fora do roadmap inicial

- Execução automática de ordens.
- Integração com corretoras.
- Day trade.
- Swing trade operacional.
- Opções.
- Cripto.
- Alavancagem.
- Operações intraday.
- Execução automática baseada em alerta informativo.
