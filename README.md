# Araripe Invest API

Backend Spring Boot do Araripe Invest. Este projeto concentra dados de mercado, fundamentos, regras financeiras deterministicas, risco, recomendacoes de position trade, auditoria, integracoes externas e APIs para o frontend.

## Stack

- Java 21
- Spring Boot
- PostgreSQL
- Flyway
- Spring Data JPA
- Spring Security
- OpenAI Responses API via adapter proprio
- Spring Mail
- Maven Wrapper

## Requisitos locais

- Java 21
- Docker e Docker Compose
- Bash ou terminal compativel

## Variaveis de ambiente

Use `.env.example` como base para um arquivo local `.env`. Segredos reais nao devem ser versionados.

| Variavel | Uso |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | Profile ativo. Para desenvolvimento, use `local`. |
| `SERVER_PORT` | Porta HTTP da API. Default local: `8080`. |
| `ARARIPE_DB_HOST` | Host do PostgreSQL. Default: `localhost`. |
| `ARARIPE_DB_PORT` | Porta do PostgreSQL. Default: `5432`. |
| `ARARIPE_DB_NAME` | Nome do banco. Default: `araripe_invest`. |
| `ARARIPE_DB_URL` | URL JDBC completa do PostgreSQL em producao. |
| `ARARIPE_DB_USERNAME` | Usuario do banco. Default: `araripe`. |
| `ARARIPE_DB_PASSWORD` | Senha do banco local. |
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
| `OPENAI_MODEL` | Modelo usado para enriquecimento contextual. Default: `gpt-5.6-luna`. |
| `OPENAI_TIMEOUT_SECONDS` | Timeout da chamada de IA. |
| `OPENAI_MAX_TOKENS` | Limite total de tokens gerados pela Responses API, incluindo raciocinio e saida estruturada. Default: `16000`. |
| `OPENAI_PROMPT_VERSION` | Versao do prompt macro/setorial persistida na auditoria. |
| `OPENAI_WEB_SEARCH_MODEL` | Modelo usado no adapter com `web_search` obrigatorio. Default: `gpt-5.4-mini`. |
| `OPENAI_REASONING_EFFORT` | Esforco de raciocinio enviado em `reasoning.effort`. Valores: `none`, `low`, `medium`, `high`, `xhigh`. Default: `high`. |
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
| `ARARIPE_ALLOWED_ORIGINS` | Origens CORS permitidas. Default local: `http://localhost:4200`. |

## Subir banco local

```bash
cp .env.example .env
docker compose up -d
```

O compose sobe um PostgreSQL local com health check e volume persistente.

## Rodar API local

Com o PostgreSQL local ativo:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`.

O health check do Actuator responde sem autenticacao em:

```text
http://localhost:8080/actuator/health
```

## Autenticacao

A API usa JWT stateless assinado com HMAC. Cadastro e login ficam publicos:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

Rotas autenticadas exigem header:

```http
Authorization: Bearer <token>
```

O endpoint `GET /api/v1/auth/me` retorna o usuario autenticado. Rotas `/api/v1/admin/**` exigem perfil `ADMIN`.

## Universo monitorado e brapi.dev

O cadastro administrativo de ativos define o universo monitorado. A integracao com brapi.dev consulta somente simbolos cadastrados e com `active=true`; simbolos ausentes ou inativos sao ignorados antes de qualquer chamada externa.

Endpoints administrativos principais:

```http
GET /api/v1/admin/assets
POST /api/v1/admin/assets
PUT /api/v1/admin/assets/{assetId}
PATCH /api/v1/admin/assets/{assetId}/status
```

O client brapi fica isolado atras dos contratos `MarketDataProvider`, `FundamentalDataProvider` e `MacroEconomicDataProvider`.

Para criar o primeiro admin local de forma controlada, configure:

```bash
ARARIPE_BOOTSTRAP_ADMIN_ENABLED=true
ARARIPE_BOOTSTRAP_ADMIN_EMAIL=admin@araripe.test
ARARIPE_BOOTSTRAP_ADMIN_PASSWORD=troque-esta-senha
```

Depois do primeiro login, desabilite o bootstrap em ambientes compartilhados.

## Testes

```bash
./mvnw test
```

## Convenções de trabalho

- Branches: `main` para versao estavel, `develop` para integracao, `feature/<escopo>`, `fix/<escopo>` e `chore/<escopo>`.
- Commits: usar Conventional Commits, como `feat:`, `fix:`, `test:`, `docs:`, `chore:` e `refactor:`.
- Tags: `vMAJOR.MINOR.PATCH`, por exemplo `v0.1.0`.
- Releases: cada release deve registrar migrations, variaveis novas, riscos operacionais e passos de rollback.

## Regras de seguranca

- Nunca expor token da brapi, OpenAI ou credenciais de e-mail/SMTP no frontend.
- Nunca gravar senhas em texto puro.
- Nunca registrar tokens, senhas ou prompts com dados sensiveis em logs.
- Falha externa deve gerar status rastreavel, nao recomendacao falsa.
