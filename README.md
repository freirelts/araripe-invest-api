# Araripe Invest API

Backend Spring Boot do Araripe Invest, responsavel por dados de mercado, fundamentos, regras financeiras deterministicas, modelos de estudo, screener, registros informativos de posicao, alertas factuais, auditoria, integracoes externas e APIs consumidas pelo frontend Angular.

O produto e estritamente educacional, informativo e analitico. A API nao recomenda compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao; nao executa ordens; nao integra corretoras; nao faz suitability; e nao substitui regras, dados, validacoes ou auditoria por IA generativa.

## Indice

- [Responsabilidades](#responsabilidades)
- [Stack](#stack)
- [Documentacao tecnica](#documentacao-tecnica)
- [Arquitetura do repositorio](#arquitetura-do-repositorio)
- [Execucao local](#execucao-local)
- [Variaveis de ambiente](#variaveis-de-ambiente)
- [Autenticacao e perfis](#autenticacao-e-perfis)
- [Contratos principais](#contratos-principais)
- [Dados, jobs e integracoes](#dados-jobs-e-integracoes)
- [Testes](#testes)
- [Convencoes de trabalho](#convencoes-de-trabalho)
- [Regras de seguranca e dominio](#regras-de-seguranca-e-dominio)

## Responsabilidades

Esta API concentra as regras e operacoes que precisam ser deterministicas, auditaveis e protegidas do frontend:

- autenticacao JWT stateless e autorizacao por perfis;
- cadastro administrativo de usuarios e ativos monitorados;
- coleta de cotacoes, OHLCV, fundamentos, demonstrativos, dividendos e indicadores macro;
- persistencia e validacao de qualidade/frescor dos dados;
- calculo de indicadores tecnicos e fundamentalistas;
- motor de modelos de estudo, filtros eliminatorios, scoring e screener;
- registros informativos de posicao declarados pelo usuario;
- alertas informativos baseados somente em fatos observaveis;
- analises economicas enriquecidas por IA com saida estruturada, validacao e auditoria;
- envio de e-mail diario consolidado somente quando houver alertas informativos;
- endpoints operacionais, administrativos e contratos para o frontend.

## Stack

- Java 21
- Spring Boot 4.1
- PostgreSQL
- Flyway
- Spring Data JPA
- Spring Security
- Spring Mail
- OpenAI Responses API via adapter proprio
- brapi.dev via adapters de dados de mercado, fundamentos e macroeconomia
- Maven Wrapper
- Testcontainers

## Documentacao tecnica

A documentacao tecnica foi movida para este repositorio em [`documentacao-tecnica/`](documentacao-tecnica/). Ela documenta o Araripe Invest para pessoas tecnicas e nao tecnicas, explicando o sistema, a logica financeira, os limites do produto e as integracoes planejadas.

### Indice tecnico

1. [Visao geral do produto](documentacao-tecnica/01-visao-geral.md)
2. [Arquitetura e integracoes](documentacao-tecnica/02-arquitetura-e-integracoes.md)
3. [Conceitos financeiros para leigos](documentacao-tecnica/03-conceitos-financeiros.md)
4. [Dados, fontes e qualidade](documentacao-tecnica/04-dados-fontes-e-qualidade.md)
5. [Motor de regras, modelos de estudo e scoring](documentacao-tecnica/05-motor-de-regras-setups-scoring.md)
6. [Gestao de risco](documentacao-tecnica/06-gestao-de-risco.md)
7. [Backtest e metricas](documentacao-tecnica/07-backtest-e-metricas.md)
8. [Modelo de dados](documentacao-tecnica/08-modelo-de-dados.md)
9. [APIs e telas](documentacao-tecnica/09-apis-e-telas.md)
10. [Operacao, seguranca e compliance](documentacao-tecnica/10-operacao-seguranca-compliance.md)
11. [Roadmap](documentacao-tecnica/11-roadmap.md)
12. [Glossario](documentacao-tecnica/12-glossario.md)
13. [Conteudos de estudo na web](documentacao-tecnica/13-conteudos-estudo-web.md)
14. [Documentacao brapi.dev usada pelo projeto](documentacao-tecnica/14-documentacao-brapi.md)
15. [Diagnostico regulatorio e plano de adequacao](documentacao-tecnica/15-diagnostico-regulatorio-adequacao.md)

### Decisao central

O Araripe Invest e uma plataforma educacional, informativa e analitica para estudo e acompanhamento de ativos brasileiros. O produto nao orienta compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao, nao executa ordens, nao integra corretora e nao faz suitability.

### Referencias externas consultadas

- brapi.dev: API REST brasileira com dados financeiros, cotacoes, historico OHLCV, dividendos, fundamentos, cripto, cambio e indicadores economicos: https://brapi.dev/ e https://brapi.dev/docs
- B3 Cotacoes Historicas: series de precos desde 1986, sem ajuste por inflacao ou proventos: https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/cotacoes-historicas/
- Banco Central SGS/BCData: interface JSON para series temporais economico-financeiras: https://api.bcb.gov.br/dados/serie/bcdata.sgs.{codigo_serie}/dados?formato=json
- Portal Dados Abertos CVM: conjuntos de dados de companhias abertas, fundos e documentos regulatorios: https://dados.cvm.gov.br/
- Resolucao CVM 80: prazos de entrega de DFP e ITR usados como referencia para frescor fundamentalista: https://conteudo.cvm.gov.br/export/sites/cvm/legislacao/resolucoes/anexos/001/resol080consolid.pdf
- OpenAI API: geracao de texto e saidas estruturadas para enriquecimento contextual: https://developers.openai.com/api/docs

## Arquitetura do repositorio

```text
.
|-- documentacao-tecnica/           # Documentacao de produto, dominio, arquitetura e compliance
|-- src/main/java/.../adapters      # Controllers REST e adapters externos
|-- src/main/java/.../application   # Casos de uso e servicos de aplicacao
|-- src/main/java/.../domain        # Entidades e tipos do dominio
|-- src/main/java/.../infrastructure
|   |-- config                      # Seguranca, JWT, CORS e clientes HTTP
|   |-- jobs                        # Agendamento operacional
|   `-- persistence                 # Repositories JPA
|-- src/main/resources/db/migration # Migracoes Flyway
|-- src/test/java                   # Testes automatizados
|-- docker-compose.yml              # PostgreSQL local
`-- .env.example                    # Variaveis locais de referencia
```

## Execucao local

### Requisitos

- Java 21
- Docker e Docker Compose
- Bash ou terminal compativel

### Subir banco local

```bash
cp .env.example .env
docker compose up -d
```

O compose sobe um PostgreSQL local com health check e volume persistente.

### Rodar API

Com o PostgreSQL local ativo:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

A API sobe em:

```text
http://localhost:8080
```

Health check do Actuator:

```text
http://localhost:8080/actuator/health
```

Contrato OpenAPI interno:

```text
http://localhost:8080/v3/api-docs
```

## Variaveis de ambiente

Use [`.env.example`](.env.example) como base para um arquivo local `.env`. Segredos reais nao devem ser versionados.

| Variavel | Uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Profile ativo. Para desenvolvimento local, use `local`. |
| `SERVER_PORT` | Porta HTTP da API. Default local: `8080`. |
| `ARARIPE_DB_HOST` | Host do PostgreSQL. Default: `localhost`. |
| `ARARIPE_DB_PORT` | Porta do PostgreSQL. Default: `5432`. |
| `ARARIPE_DB_NAME` | Nome do banco. Default: `araripe_invest`. |
| `ARARIPE_DB_URL` | URL JDBC completa do PostgreSQL em producao. |
| `ARARIPE_DB_USERNAME` | Usuario do banco. Default: `araripe`. |
| `ARARIPE_DB_PASSWORD` | Senha do banco local. |
| `ARARIPE_ALLOWED_ORIGINS` | Origens CORS permitidas. Default local: `http://localhost:4200`. |
| `ARARIPE_JWT_ISSUER` | Emissor esperado dos tokens JWT. Default: `araripe-invest-api`. |
| `ARARIPE_JWT_AUDIENCE` | Audiencia esperada dos tokens JWT. Default: `araripe-invest-fed`. |
| `ARARIPE_JWT_SECRET` | Segredo HMAC para assinar JWT. Use valor forte e com pelo menos 32 bytes. |
| `ARARIPE_JWT_EXPIRATION` | Duracao do token no formato ISO-8601, como `PT2H`. |
| `ARARIPE_BOOTSTRAP_ADMIN_ENABLED` | Habilita criacao do primeiro admin no start. Default: `false`. |
| `ARARIPE_BOOTSTRAP_ADMIN_EMAIL` | E-mail do primeiro admin quando o bootstrap estiver habilitado. |
| `ARARIPE_BOOTSTRAP_ADMIN_PASSWORD` | Senha inicial do primeiro admin. Exige pelo menos 12 caracteres. |
| `ARARIPE_BOOTSTRAP_ADMIN_NAME` | Nome do primeiro admin. |
| `BRAPI_BASE_URL` | URL base da brapi. Default: `https://brapi.dev/api`. |
| `BRAPI_API_TOKEN` | Token da brapi, usado somente no backend e nunca registrado em logs. |
| `BRAPI_TIMEOUT_SECONDS` | Timeout por chamada externa. |
| `BRAPI_RETRY_MAX_ATTEMPTS` | Limite de tentativas para brapi. |
| `OPENAI_API_KEY` | Chave OpenAI usada somente no backend. |
| `OPENAI_ENABLED` | Habilita o adapter OpenAI Responses API; sem chave o backend usa fallback deterministico auditavel. |
| `OPENAI_MODEL` | Modelo base configurado para enriquecimento contextual. Default da aplicacao: `gpt-5.6-luna`; exemplo local: `gpt-5.4-mini`. |
| `OPENAI_BASE_URL` | URL base da OpenAI API. Default: `https://api.openai.com/v1`. |
| `OPENAI_TIMEOUT_SECONDS` | Timeout da chamada de IA. |
| `OPENAI_MAX_TOKENS` | Limite total de tokens gerados pela Responses API, incluindo raciocinio e saida estruturada. Default: `16000`. |
| `OPENAI_PROMPT_VERSION` | Versao do prompt macro/setorial persistida na auditoria. |
| `OPENAI_WEB_SEARCH_MODEL` | Modelo usado no adapter com `web_search` obrigatorio. Default da aplicacao: `gpt-5.4`. |
| `OPENAI_REASONING_EFFORT` | Esforco de raciocinio enviado em `reasoning.effort`. Valores: `none`, `low`, `medium`, `high`, `xhigh`. Default: `medium`. |
| `OPENAI_WEB_SEARCH_CONTEXT_SIZE` | Tamanho de contexto da ferramenta `web_search`. Valores: `low`, `medium`, `high`. |
| `SPRING_MAIL_HOST` | Host SMTP para envio dos e-mails consolidados. |
| `SPRING_MAIL_PORT` | Porta SMTP. Default local sugerido: `587`. |
| `SPRING_MAIL_USERNAME` | Usuario SMTP, quando aplicavel. |
| `SPRING_MAIL_PASSWORD` | Senha SMTP, quando aplicavel. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | Habilita autenticacao SMTP. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | Habilita STARTTLS no SMTP. |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | Habilita SSL direto no SMTP quando o provider exigir. |
| `ARARIPE_MAIL_HEALTH_ENABLED` | Habilita o health check SMTP do Actuator quando houver provider configurado. Default: `false`. |
| `ARARIPE_MAIL_FROM` | Remetente dos e-mails consolidados. |
| `ARARIPE_MAIL_REPLY_TO` | Reply-to dos e-mails consolidados, quando aplicavel. |

## Autenticacao e perfis

A API usa JWT stateless assinado com HMAC.

Endpoints publicos:

```http
POST /api/v1/auth/login
GET /api/v1/legal/terms/current
GET /actuator/health
GET /v3/api-docs
```

Rotas autenticadas exigem header:

```http
Authorization: Bearer <token>
```

Endpoints de sessao:

```http
GET /api/v1/auth/me
```

Perfis principais:

| Perfil | Acesso |
| --- | --- |
| `CUSTOMER` | Dashboards, screener, modelos de estudo, registros informativos de posicao, alertas informativos e historico. |
| `ADMIN` | Tudo que `CUSTOMER` acessa, mais cadastro de usuarios, ativos monitorados, jobs e solicitacoes administrativas de IA. |

Cadastro publico nao fica habilitado. A criacao de usuarios acontece somente por perfil `ADMIN`:

```http
POST /api/v1/admin/users
```

Exemplo de criacao administrativa:

```json
{
  "name": "Cliente",
  "email": "cliente@exemplo.com",
  "password": "senha-forte-com-12-caracteres",
  "status": "ACTIVE",
  "subscriptionStatus": "ACTIVE",
  "roles": ["CUSTOMER"]
}
```

Para criar o primeiro admin local de forma controlada:

```bash
ARARIPE_BOOTSTRAP_ADMIN_ENABLED=true
ARARIPE_BOOTSTRAP_ADMIN_EMAIL=admin@araripe.test
ARARIPE_BOOTSTRAP_ADMIN_PASSWORD=troque-esta-senha
```

Depois do primeiro login, desabilite o bootstrap em ambientes compartilhados.

## Contratos principais

### Usuario e termos

```http
POST /api/v1/auth/login
GET /api/v1/auth/me
GET /api/v1/legal/terms/current
```

### Ativos, screener e modelos de estudo

```http
GET /api/v1/assets
GET /api/v1/assets/{symbol}/fundamentals
GET /api/v1/assets/{symbol}/diagnostics
GET /api/v1/screener
GET /api/v1/asset-studies/{studyId}
GET /api/v1/asset-studies/history
```

### Registros informativos de posicao

```http
GET /api/v1/position-records
GET /api/v1/position-records/history
POST /api/v1/position-records
PUT /api/v1/position-records/{positionId}
PATCH /api/v1/position-records/{positionId}/close
POST /api/v1/position-records/{positionId}/contributions
POST /api/v1/position-records/{positionId}/quantity-adjustments
POST /api/v1/position-records/{positionId}/quantity-reductions
POST /api/v1/position-records/{positionId}/main-thesis
PATCH /api/v1/position-records/{positionId}/main-thesis
```

Esses endpoints registram fatos declarados pelo usuario. Eles nao calculam quantidade sugerida, alocacao individualizada, lucro realizado ou comando operacional.

### Ativos acompanhados e alertas informativos

```http
GET /api/v1/watched-assets
POST /api/v1/watched-assets
PUT /api/v1/watched-assets/{watchItemId}
PATCH /api/v1/watched-assets/{watchItemId}/archive

GET /api/v1/alerts
GET /api/v1/alerts/{alertId}
PATCH /api/v1/alerts/{alertId}/read
```

Alertas representam somente fatos observaveis, rastreados por fonte, data de referencia e regra informativa.

### IA contextual

```http
GET /api/v1/ai/context-analyses
GET /api/v1/ai/context-analyses/{analysisId}
POST /api/v1/admin/ai/context-analyses
```

A IA pode enriquecer explicacoes, mas nao decide resultados, nao aprova alertas bloqueados por filtros financeiros e nao substitui validacoes deterministicas.

### Administracao e operacao

```http
GET /api/v1/admin/users
POST /api/v1/admin/users
PUT /api/v1/admin/users/{userId}

GET /api/v1/admin/assets
POST /api/v1/admin/assets
PUT /api/v1/admin/assets/{assetId}
PATCH /api/v1/admin/assets/{assetId}/status

GET /api/v1/jobs/status
GET /api/v1/admin/jobs/status
POST /api/v1/admin/jobs/{jobName}/runs
```

Contratos antigos de conduta, aliases e parametros pessoais de decisao nao fazem parte da API atual.

## Dados, jobs e integracoes

O cadastro administrativo de ativos define o universo monitorado. A integracao com brapi.dev consulta somente simbolos cadastrados e com `active=true`; simbolos ausentes ou inativos sao ignorados antes de qualquer chamada externa.

O client brapi fica isolado atras destes contratos:

- `MarketDataProvider`
- `FundamentalDataProvider`
- `MacroEconomicDataProvider`

O adapter outbound da brapi usa Spring Cloud OpenFeign para as chamadas HTTP declarativas. O token e aplicado somente como header `Authorization: Bearer ...` no backend, e o adapter preserva o limite defensivo de 5 simbolos por chamada externa.

A OpenAI Responses API fica isolada atras de:

- `EconomicContextAiProvider`

E-mail/SMTP fica isolado atras de:

- `NotificationProvider`

Falhas criticas interrompem etapas dependentes que possam produzir screener, alertas ou e-mails com dados incorretos. Falhas externas devem gerar status rastreavel, nao alerta falso.

## Testes

Executar a suite automatizada:

```bash
./mvnw test
```

Para mudancas em regras financeiras, modelos de estudo, alertas, scoring, validacao de IA, dados ou migrations, inclua ou atualize testes automatizados relevantes.

## Convencoes de trabalho

- Branches: `main` para versao estavel, `develop` para integracao, `feature/<escopo>`, `fix/<escopo>` e `chore/<escopo>`.
- Commits: usar Conventional Commits, como `feat:`, `fix:`, `test:`, `docs:`, `chore:` e `refactor:`.
- Tags: `vMAJOR.MINOR.PATCH`, por exemplo `v0.1.0`.
- Releases: cada release deve registrar migrations, variaveis novas, riscos operacionais e passos de rollback.

## Regras de seguranca e dominio

- Nunca expor token da brapi, OpenAI ou credenciais de e-mail/SMTP no frontend.
- Nunca gravar senhas em texto puro.
- Nunca registrar tokens, senhas ou prompts com dados sensiveis em logs.
- Nunca gerar texto que oriente compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao.
- Dados incompletos, inconsistentes, iliquidos ou desatualizados devem bloquear resultados que dependem deles.
- Regras financeiras, screeners e alertas devem ser deterministicos, explicaveis, auditaveis e acompanhados de fonte e data de referencia.
- Falha externa deve gerar status rastreavel, nao alerta falso.
- E-mails consolidados devem conter apenas alertas informativos dos ativos acompanhados, sem tipo de recomendacao ou comando operacional.
