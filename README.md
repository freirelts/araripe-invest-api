# Araripe Invest API

Backend Spring Boot do Araripe Invest. Este projeto concentra dados de mercado, fundamentos, regras financeiras deterministicas, risco, recomendacoes de position trade, auditoria, integracoes externas e APIs para o frontend.

## Stack

- Java 21
- Spring Boot
- PostgreSQL
- Flyway
- Spring Data JPA
- Spring Security
- Spring AI/OpenAI
- AWS SDK SNS
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
| `OPENAI_API_KEY` | Chave OpenAI para Spring AI. |
| `OPENAI_ENABLED` | Habilita o adapter OpenAI quando houver `ChatClient`; sem modelo configurado o backend usa fallback deterministico auditavel. |
| `OPENAI_MODEL` | Modelo usado para enriquecimento contextual. Default: `gpt-5.6-luna`. |
| `OPENAI_TIMEOUT_SECONDS` | Timeout da chamada de IA. |
| `OPENAI_MAX_TOKENS` | Limite de tokens de saida para o contexto estruturado. |
| `OPENAI_PROMPT_VERSION` | Versao do prompt macro/setorial persistida na auditoria. |
| `SPRING_AI_MODEL_CHAT` | Provider do modelo de chat. Default: `openai`. Em testes automatizados fica desabilitado. |
| `SPRING_AI_MODEL_EMBEDDING` | Provider de embeddings. Default local: `none`. |
| `SPRING_AI_MODEL_IMAGE` | Provider de imagem. Default local: `none`. |
| `SPRING_AI_MODEL_AUDIO_SPEECH` | Provider de audio speech. Default local: `none`. |
| `SPRING_AI_MODEL_AUDIO_TRANSCRIPTION` | Provider de transcricao. Default local: `none`. |
| `SPRING_AI_MODEL_MODERATION` | Provider de moderacao. Default local: `none`. |
| `AWS_REGION` | Regiao AWS para SNS. |
| `AWS_SNS_TOPIC_ARN` | Topico SNS para e-mails consolidados. |
| `SNS_EMAIL_SENDER` | Identificacao operacional do remetente/configuracao. |
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

- Nunca expor token da brapi, OpenAI ou AWS no frontend.
- Nunca gravar senhas em texto puro.
- Nunca registrar tokens, senhas ou prompts com dados sensiveis em logs.
- Falha externa deve gerar status rastreavel, nao recomendacao falsa.
