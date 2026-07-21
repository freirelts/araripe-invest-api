# APIs E Telas

Este documento descreve os contratos atuais depois da revisão regulatória de linguagem. A API pública do MVP deve expor modelos de estudo, registros informativos de posição real, alertas informativos, termos, dados e operação.

## APIs Planejadas Do `araripe-invest-api`

### Autenticação

```http
POST /api/v1/auth/login
GET /api/v1/auth/me
```

Login e dados básicos do usuário autenticado. Cadastro público não deve ficar exposto; criação de usuários é função administrativa.

### Screener De Modelos De Estudo

```http
GET /api/v1/screener?date=2026-07-07
GET /api/v1/screener?symbol=WEGE3
```

Retorna lista filtrável e ordenável por critérios escolhidos pelo usuário.

O parâmetro opcional `symbol` restringe o resultado aos modelos de estudo do ativo informado, sem exigir filtro local na interface.

Quando `date` não for informado, o backend deve usar a `reference_date` da última execução bem-sucedida registrada em `job_runs` para o job que materializa o screener, em vez de assumir a data do calendário atual. Isso evita telas vazias em fins de semana, feriados ou dias em que o fluxo operacional ainda não executou com sucesso.

Campos principais:

- ativo;
- nome;
- setor;
- tipo de modelo de estudo;
- estado do estudo;
- score como aderência a critérios;
- preço atual;
- preço de referência do estudo;
- preço justo estimado;
- margem de segurança educacional;
- qualidade fundamentalista;
- valuation;
- liquidez;
- contexto macro;
- motivos;
- fontes;
- data de referência.

O screener não deve usar ordenação padrão por melhor ativo ou melhor decisão. A ordenação padrão deve ser neutra, como ordem alfabética ou data de atualização, e o usuário pode escolher indicadores.

### Detalhe Do Modelo De Estudo

```http
GET /api/v1/asset-studies/{studyId}
GET /api/v1/asset-studies/history?symbol=WEGE3
```

Retorna detalhes do modelo de estudo:

- composição do score;
- filtros aplicados;
- motivos;
- indicadores técnicos de longo prazo;
- indicadores fundamentalistas;
- dividendos;
- valuation educacional;
- preço de referência do estudo;
- fontes e datas;
- `aiContext`, com contexto econômico enriquecido por IA, fontes e status de validação, somente quando houver análise validada persistida;
- aviso de escopo educacional.

O detalhe pode permitir associar o modelo a uma posição declarada, usando linguagem como `Acompanhar estudo`. Não usar linguagem de compra, venda, aumento, redução ou manutenção.

### Ativos Acompanhados Sem Posição

```http
GET /api/v1/watched-assets
POST /api/v1/watched-assets
PUT /api/v1/watched-assets/{watchedAssetId}
PATCH /api/v1/watched-assets/{watchedAssetId}/archive
```

Fora do MVP. No MVP não há acompanhamento de ativo sem posição declarada; a interface deve centralizar limiares, associação de modelo de estudo e alterações de quantidade em registros informativos de posição.

Em versão futura, permite ao `CUSTOMER` cadastrar ativos acompanhados sem posição e associar opcionalmente um modelo de estudo.

Campos principais:

- ativo;
- observações;
- modelo de estudo acompanhado;
- limiar inferior de preço definido pelo usuário;
- limiar superior de preço definido pelo usuário;
- status do acompanhamento;
- datas de criação e atualização.

Se houver registro de posição real, o texto deve deixar claro que é cadastro informativo do usuário. O sistema pode mostrar histórico e exposição informativa, mas não pode gerar quantidade, aporte, alocação individualizada ou comando operacional.

### Registros Informativos De Posição

```http
GET /api/v1/position-records
GET /api/v1/position-records/history
POST /api/v1/position-records
PUT /api/v1/position-records/{positionId}
POST /api/v1/position-records/{positionId}/quantity-adjustments
POST /api/v1/position-records/{positionId}/quantity-reductions
PATCH /api/v1/position-records/{positionId}/close
POST /api/v1/position-records/{positionId}/main-thesis
PATCH /api/v1/position-records/{positionId}/main-thesis
```

Permite ao `CUSTOMER` manter cadastro informativo de posição real declarada. O endpoint `quantity-adjustments` registra novo aporte declarado pelo usuário e recalcula o preço médio ponderado. O endpoint `quantity-reductions` registra redução declarada pelo usuário, preserva o preço médio e reduz apenas a quantidade; se a quantidade declarada for igual à quantidade atual, o registro é fechado preservando histórico.

Por padrão, a listagem principal retorna somente posições abertas. Posições fechadas ficam disponíveis em endpoint/tela de histórico e não devem entrar nas contagens operacionais nem na listagem principal de ativos acompanhados por posição declarada.

Essas operações não calculam quantidade sugerida, alocação individualizada, lucro realizado ou comando operacional.

### Alertas Informativos

```http
GET /api/v1/alerts?from=2026-07-01&to=2026-07-07
GET /api/v1/alerts/{alertId}
PATCH /api/v1/alerts/{alertId}/read
```

Retorna eventos informativos gerados após o fluxo operacional.

Quando `to` não for informado, a busca deve usar a `reference_date` da última varredura de registros informativos bem-sucedida registrada em `job_runs`. O intervalo padrão continua retrocedendo 30 dias a partir dessa data operacional efetiva.

Campos principais:

- posição declarada, quando houver;
- ativo;
- modelo de estudo relacionado, quando houver;
- tipo do evento;
- severidade informativa;
- preço atual;
- limiar cruzado, quando aplicável;
- indicador afetado;
- motivos determinísticos;
- fontes usadas;
- data de referência;
- estado de envio no e-mail diário consolidado;
- aviso de que o alerta não representa recomendação individualizada.

Tipos iniciais:

- `PRICE_THRESHOLD_REACHED`
- `DATA_UPDATED`
- `DATA_STALE`
- `INDICATOR_THRESHOLD_REACHED`
- `STUDY_ASSUMPTION_CHANGED`
- `QUALITY_DATA_BLOCKED`

Um alerta deve descrever apenas o fato observado. Exemplo: "O preço cruzou o limiar superior definido por você." Não incluir verbos de ação operacional.

### E-Mail Diário Consolidado

Não há endpoint público separado de notificações. A leitura web usa os endpoints de alertas informativos.

Regra de negócio:

- ao final do processamento, cada cliente recebe no máximo um e-mail por data de referência;
- o e-mail contém somente alertas informativos das posições declaradas;
- o e-mail pode ser enviado em HTML responsivo com fallback em texto simples equivalente;
- cada item do e-mail deve apresentar tipo legível, severidade legível, fato observado, motivo determinístico extraído das evidências e auditoria com fonte, data de referência e regra;
- alertas de premissa alterada devem explicar o gatilho objetivo disponível, como status atual do estudo, score aceito, score atual, variação de score e filtros determinísticos relevantes;
- se não houver alerta informativo, nenhum e-mail deve ser enviado;
- eventos objetivos ficam persistidos em `informational_alerts` com status de envio, provider, tentativas, fonte, data de referência e versão da regra.

### Termos De Uso

```http
GET /api/v1/legal/terms/current
```

Retorna a versão vigente dos termos educacionais e informativos. Quando houver fluxo de aceite, o sistema deve registrar a versão atual em `termsVersionAccepted` e `termsAcceptedAt`.

Conteúdo mínimo:

- escopo educacional, informativo e analítico;
- ausência de consultoria, análise credenciada, administração de carteira, suitability, intermediação e execução de ordens;
- ausência de recomendação individualizada;
- ausência de promessa de retorno, segurança ou decisão automatizada;
- riscos de mercado e limitações de dados;
- aviso de revisão jurídica antes de uso comercial amplo.

### Fundamentos Do Ativo

```http
GET /api/v1/assets/{symbol}/fundamentals
```

Retorna fundamentos normalizados do ativo, incluindo estatísticas, dados financeiros, balanço, DRE, fluxo de caixa e dividendos disponíveis.

### Ativos Monitorados

```http
GET /api/v1/assets
```

Lista ativos configurados para análise. Cadastro, edição, ativação e inativação dos ativos são funções administrativas.

### Preferências De Alerta

```http
GET /api/v1/alert-preferences
PUT /api/v1/alert-preferences
```

Permite configurar preferências de alerta, como canais, horários e limiares informativos. Essas preferências não podem ser usadas para inferir suitability ou orientar conduta.

### Análise Educacional Com IA

```http
POST /api/v1/asset-studies/{studyId}/ai-context-analysis
GET /api/v1/asset-studies/{studyId}/ai-context-analysis/latest
```

Executa ou consulta contexto macro/setorial educacional com IA.

Regras:

- o backend monta o pacote de entrada com dados determinísticos e snapshots macro persistidos;
- a IA deve usar `web_search` e retornar fontes externas auditáveis;
- resposta sem fonte externa válida deve ser rejeitada;
- a IA não pode orientar compra, venda, manutenção, aumento, redução, alocação ou encerramento;
- falha ou timeout da IA não altera screener, modelo de estudo ou alerta determinístico.

### Status Operacional

```http
GET /api/v1/operations/daily-runs
GET /api/v1/operations/daily-runs/{runId}
POST /api/v1/operations/daily-runs/manual
```

Permite a administradores acompanhar coletas, indicadores, modelos de estudo, screener e alertas informativos.

Nas APIs web de status operacional sem data explícita, a data efetiva deve ser a última `reference_date` com execução bem-sucedida em `job_runs`, priorizando o fluxo operacional diário. Se não houver run bem-sucedida registrada, o sistema pode cair para a data do calendário atual apenas como fallback inicial.

## Telas Do Frontend

### Landing Page

Primeira tela pública com descrição educacional do produto e botão de login. Não usar promessa de retorno, decisão automatizada ou linguagem operacional.

### Dashboard

Visão executiva com:

- status da última atualização;
- quantidade de ativos monitorados;
- distribuição dos estados dos modelos de estudo;
- alertas informativos recentes;
- qualidade dos dados;
- cards de fontes e datas.

### Screener

Lista ordenável por critérios. Deve permitir filtro por setor, liquidez, qualidade dos dados, score de aderência, valuation educacional e estado do modelo de estudo.

### Detalhe Do Modelo De Estudo

Exibe:

- dados do ativo;
- critérios atendidos e não atendidos;
- fundamentos;
- valuation educacional;
- faixa de observação do estudo;
- preço de referência do estudo;
- margem de segurança;
- fontes;
- datas;
- premissas;
- limitações;
- contexto de IA validado, quando houver.

### Registros Informativos De Posição

Permite cadastrar e manter posições reais declaradas pelo usuário como registros informativos. No MVP não deve existir tela separada para acompanhar ativo sem posição.

Campos:

- ativo;
- quantidade declarada;
- preço médio declarado;
- data inicial;
- limiar inferior de preço definido pelo usuário;
- limiar superior de preço definido pelo usuário;
- modelo de estudo acompanhado;
- acréscimos e reduções de quantidade declarados pelo usuário;
- observações;
- alertas recentes.

### Alertas Informativos

Lista eventos objetivos com filtros por tipo, severidade, ativo e data. Não deve existir botão ou texto que indique conduta operacional.

### Histórico E Auditoria

Mostra trilha de fontes, datas, regras, versões, coletas, bloqueios de qualidade e execuções do fluxo diário.

### Área Admin

Permite gerenciar usuários, ativos monitorados, fontes, parâmetros técnicos, status operacional e jobs.

```http
GET /api/v1/admin/users
POST /api/v1/admin/users
PUT /api/v1/admin/users/{userId}
```

Criação, edição de status, assinatura e perfis de usuário ficam restritas ao perfil `ADMIN`.

## Textos Permitidos

- "Alerta informativo"
- "Insight educacional"
- "Modelo de estudo"
- "Hipótese de análise"
- "Screener"
- "Faixa de observação do estudo"
- "Preço de referência do estudo"
- "Limiar inferior de preço definido pelo usuário"
- "Limiar superior de preço definido pelo usuário"
- "Ponto de reavaliação"

## Textos Proibidos Na Interface

- Promessa de retorno.
- Ausência de risco.
- Melhor ativo para ganhar dinheiro.
- Qualquer comando de compra, venda, manutenção, aumento, redução, execução ou encerramento.
