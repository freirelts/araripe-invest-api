# Diagnóstico regulatório e adequação

Data da auditoria: 2026-07-16

## Diagnóstico atual

O Araripe Invest deve operar como plataforma educacional, informativa e analítica para estudo e acompanhamento de ativos brasileiros. O produto não presta consultoria de valores mobiliários, análise credenciada, administração de carteira, suitability, intermediação ou execução de ordens.

O escopo permitido é:

- organizar dados públicos e dados declarados pelo usuário;
- calcular indicadores, filtros, score de aderência a critérios, valuation educacional e margem de segurança;
- exibir modelos de estudo com metodologia, fonte, data de referência, limitações e trilha de auditoria;
- emitir alertas informativos factuais de preço, dado, indicador, premissa ou qualidade;
- enriquecer contexto econômico com IA generativa, sem decisão automatizada de conduta.

O escopo proibido é:

- orientar compra, venda, manutenção, aumento, redução, alocação ou encerramento de posição;
- sugerir quantidade, aporte, rebalanceamento ou exposição individualizada;
- apresentar promessa de retorno, segurança, rentabilidade ou proteção;
- executar ordens, integrar corretora ou intermediar operação;
- usar IA para substituir regras determinísticas, dados auditáveis ou revisão humana.

## Pontos de atenção

### Linguagem

Todo texto exposto ao usuário deve usar vocabulário informativo. Modelos de estudo, scores e alertas devem explicar critérios e fatos observados, sem conclusão de conduta.

Botões, cards, e-mails, estados vazios e mensagens de erro não devem usar verbos de ação operacional. Quando houver dúvida, preferir termos como "acompanhar", "ver detalhes", "ver evidências", "alerta informativo", "premissa alterada" e "dado bloqueado por qualidade".

### Dados de posição real

Dados de posição real são cadastro informativo declarado pelo usuário. O sistema pode exibir histórico, preço médio, quantidade e limiares informativos, mas não deve converter esses dados em orientação individualizada.

### Modelos de estudo

Modelos de estudo devem conter:

- tipo de estudo;
- score de aderência a critérios;
- fundamentos e indicadores usados;
- valuation educacional;
- preço de referência do estudo;
- margem de segurança;
- filtros aplicados;
- fontes e datas;
- versão de regra;
- limitações.

Dados incompletos, ilíquidos, desatualizados ou inconsistentes devem bloquear o modelo de estudo e impedir alerta informativo sem base auditável.

### Alertas informativos

Alertas devem representar eventos factuais:

- limiar de preço definido pelo usuário foi atingido;
- dado foi atualizado;
- dado ficou desatualizado;
- indicador cruzou referência analítica;
- premissa do estudo mudou;
- qualidade de dados bloqueou o estudo.

Cada alerta deve preservar fonte, data de referência, regra aplicada, evidências e aviso de que não representa recomendação individualizada.

### IA generativa

A IA generativa pode enriquecer contexto macroeconômico, setorial ou institucional. A saída deve ser validada por schema, fonte externa auditável e regra de linguagem.

A IA não pode:

- decidir status de estudo;
- liberar bloqueio de qualidade;
- alterar alerta informativo;
- orientar conduta;
- ocultar falta de dados.

## Controles obrigatórios

- Testes automatizados contra linguagem operacional em alertas, e-mails e respostas de IA.
- OpenAPI sem contratos de recomendação, conduta, quantidade ou alocação individualizada.
- Logs e auditoria com fonte, data de referência, versão da regra e erro rastreável.
- Termos de uso com escopo educacional/informativo e aceite explícito.
- Revisão jurídica antes de comercialização ampla.

## Critério de prontidão

O produto só deve avançar para oferta comercial ampla quando:

- documentação, API e frontend usarem apenas contratos atuais;
- nenhuma superfície pública sugerir ação operacional;
- regras de bloqueio de qualidade estiverem cobertas por testes;
- e-mails e alertas estiverem rastreáveis;
- termos e disclaimers passarem por revisão jurídica.
