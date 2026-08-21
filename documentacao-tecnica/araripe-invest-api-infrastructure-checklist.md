# Plano de infraestrutura e CI/CD — Araripe Invest API

## 1. Objetivo

Este documento descreve o passo a passo para publicar a `araripe-invest-api` na AWS com uma arquitetura simples, econômica e adequada para uma primeira versão de produção.

A solução utilizará:

- Terraform para provisionamento da infraestrutura;
- Ansible para configuração da EC2;
- Docker para empacotamento e execução da aplicação;
- Amazon ECR para armazenamento das imagens;
- Amazon EC2 para execução da API;
- Amazon RDS PostgreSQL para persistência;
- Nginx como proxy reverso;
- Certbot e Let's Encrypt para HTTPS;
- Route 53 para DNS;
- GitHub Actions para CI/CD;
- GitHub OIDC para autenticação temporária na AWS;
- AWS Systems Manager para deploy sem exposição de SSH.

---

## 2. Arquitetura proposta

```text
Internet
   |
   v
Route 53
api.<dominio>
   |
   v
Elastic IP
   |
   v
EC2 pública
├── Nginx :80/:443
├── Certbot
├── Docker
└── araripe-invest-api :8080
         |
         v
RDS PostgreSQL privado
```

### Decisões arquiteturais

- Não utilizar Application Load Balancer nesta primeira versão.
- Não utilizar ACM nesta primeira versão.
- Terminar o HTTPS diretamente no Nginx.
- Utilizar certificado gratuito do Let's Encrypt por meio do Certbot.
- Manter a porta `8080` acessível apenas localmente na EC2.
- Não expor o RDS publicamente.
- Permitir acesso ao PostgreSQL somente a partir do Security Group da EC2.
- Utilizar RDS Single-AZ para reduzir custos.
- Utilizar uma única EC2 nesta primeira versão.
- Utilizar AWS Systems Manager em vez de SSH no pipeline.
- Utilizar GitHub OIDC em vez de credenciais AWS permanentes.

---

## 3. Premissas e valores a definir

Antes de iniciar, preencher os valores abaixo.

| Item | Valor |
| --- | --- |
| Região AWS | `<AWS_REGION>` |
| Domínio raiz | `<DOMAIN_NAME>` |
| Subdomínio da API | `api.<DOMAIN_NAME>` |
| E-mail do Certbot | `<CERTBOT_EMAIL>` |
| Ambiente | `production` |
| Nome do repositório ECR | `araripe-invest-api` |
| Nome do banco | `araripe_invest` |
| Usuário do banco | `<ARARIPE_DB_USERNAME>` |
| Classe inicial da EC2 | `<EC2_INSTANCE_TYPE>` |
| Classe inicial do RDS | `db.t4g.micro` |
| Volume inicial do RDS | `20 GiB gp3` |
| Retenção de backup | `7 dias` |

### Sugestão inicial

```text
EC2: t3.small ou t4g.small
RDS: db.t4g.micro
RDS: Single-AZ
RDS storage: 20 GiB gp3
```

> Validar compatibilidade da imagem Docker e da AMI antes de escolher arquitetura ARM com instâncias `t4g`.

---

# PARTE I — Preparação da aplicação

## 4. Checklist de preparação do repositório

### 4.1 Criar branch de infraestrutura

- [ ] Atualizar a branch `main`.
- [ ] Criar uma branch de trabalho.

```bash
git checkout main
git pull
git checkout -b feat/aws-infrastructure
```

---

## 4.2 Criar estrutura de diretórios

- [ ] Criar os diretórios:

```text
.
├── .github/
│   └── workflows/
│       ├── ci.yml
│       ├── deploy-production.yml
│       └── terraform.yml
├── deploy/
│   ├── docker-compose.production.yml
│   ├── nginx/
│   │   └── araripe-invest-api.conf
│   └── scripts/
│       ├── deploy.sh
│       ├── health-check.sh
│       └── rollback.sh
├── infra/
│   ├── ansible/
│   │   ├── inventories/
│   │   │   └── production/
│   │   ├── roles/
│   │   │   ├── base/
│   │   │   ├── docker/
│   │   │   ├── nginx/
│   │   │   ├── certbot/
│   │   │   └── application/
│   │   ├── ansible.cfg
│   │   └── site.yml
│   └── terraform/
│       ├── bootstrap/
│       ├── environments/
│       │   └── production/
│       └── modules/
│           ├── network/
│           ├── security-groups/
│           ├── ec2/
│           ├── rds/
│           ├── ecr/
│           ├── route53/
│           ├── iam/
│           └── github-oidc/
└── docs/
    └── infrastructure-deployment-plan.md
```

---

## 4.3 Criar Dockerfile da API

- [ ] Criar `Dockerfile`.
- [ ] Usar build multi-stage.
- [ ] Compilar com Java 21.
- [ ] Executar a aplicação com usuário não root.
- [ ] Não copiar `.env` para a imagem.
- [ ] Não incluir Maven cache desnecessário.
- [ ] Expor somente a porta `8080`.
- [ ] Adicionar health check ou garantir suporte pelo compose.
- [ ] Validar compatibilidade da imagem com a arquitetura da EC2.

Exemplo inicial:

```dockerfile
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre

RUN groupadd --system araripe \
    && useradd --system --gid araripe --home-dir /app araripe

WORKDIR /app

COPY --from=builder /workspace/target/*.jar app.jar

USER araripe

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### Validação

- [ ] Executar os testes.

```bash
./mvnw clean verify
```

- [ ] Criar a imagem localmente.

```bash
docker build -t araripe-invest-api:local .
```

- [ ] Validar que a aplicação inicia em container.

---

## 4.4 Criar `.dockerignore`

- [ ] Criar `.dockerignore`.

```text
.git
.github
.idea
.vscode
target
.env
*.log
infra
docs
documentacao-tecnica
docker-compose.yml
```

> Não excluir arquivos necessários ao build Maven.

---

## 4.5 Criar profile de produção

- [ ] Criar:

```text
src/main/resources/application-production.properties
```

Exemplo:

```properties
spring.datasource.url=${ARARIPE_DB_URL}
spring.datasource.username=${ARARIPE_DB_USERNAME}
spring.datasource.password=${ARARIPE_DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false

server.forward-headers-strategy=framework

management.endpoint.health.show-details=never
management.endpoint.health.probes.enabled=true
management.endpoints.web.exposure.include=health,info
```

### Regras

- [ ] Não definir credenciais padrão para produção.
- [ ] Falhar a inicialização quando variáveis obrigatórias estiverem ausentes.
- [ ] Manter `spring.jpa.hibernate.ddl-auto=validate`.
- [ ] Manter Flyway habilitado.
- [ ] Não expor detalhes internos do health check.
- [ ] Configurar corretamente os forwarded headers enviados pelo Nginx.

---

## 4.6 Revisar variáveis de produção

Variáveis mínimas:

```text
SPRING_PROFILES_ACTIVE=production
SERVER_PORT=8080

ARARIPE_DB_URL=
ARARIPE_DB_USERNAME=
ARARIPE_DB_PASSWORD=

ARARIPE_ALLOWED_ORIGINS=
ARARIPE_JWT_ISSUER=
ARARIPE_JWT_AUDIENCE=
ARARIPE_JWT_SECRET=
ARARIPE_JWT_EXPIRATION=PT2H

BRAPI_BASE_URL=https://brapi.dev/api
BRAPI_API_TOKEN=
BRAPI_TIMEOUT_SECONDS=90
BRAPI_RETRY_MAX_ATTEMPTS=3

OPENAI_ENABLED=
OPENAI_API_KEY=
OPENAI_MODEL=
OPENAI_BASE_URL=https://api.openai.com/v1
OPENAI_TIMEOUT_SECONDS=600
OPENAI_MAX_TOKENS=16000
OPENAI_PROMPT_VERSION=
OPENAI_WEB_SEARCH_MODEL=
OPENAI_REASONING_EFFORT=medium
OPENAI_WEB_SEARCH_CONTEXT_SIZE=medium

SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=false
ARARIPE_MAIL_HEALTH_ENABLED=false
ARARIPE_MAIL_FROM=
ARARIPE_MAIL_REPLY_TO=

ARARIPE_BOOTSTRAP_ADMIN_ENABLED=false
ARARIPE_BOOTSTRAP_ADMIN_EMAIL=
ARARIPE_BOOTSTRAP_ADMIN_PASSWORD=
ARARIPE_BOOTSTRAP_ADMIN_NAME=
```

### Segurança

- [ ] Gerar segredo JWT forte.
- [ ] Nunca usar o segredo padrão de desenvolvimento.
- [ ] Não versionar `.env.production`.
- [ ] Não imprimir tokens nos logs.
- [ ] Não armazenar `OPENAI_API_KEY`, `BRAPI_API_TOKEN` ou senha do banco na imagem.
- [ ] Não armazenar credenciais AWS permanentes no GitHub.

---

## 4.7 Criar compose de produção

- [ ] Manter o `docker-compose.yml` atual somente para desenvolvimento local.
- [ ] Criar `deploy/docker-compose.production.yml`.
- [ ] Não subir PostgreSQL local em produção.
- [ ] Usar a imagem publicada no ECR.
- [ ] Vincular a porta da API ao loopback da EC2.

Exemplo:

```yaml
services:
  api:
    image: ${ARARIPE_IMAGE}
    container_name: araripe-invest-api
    restart: unless-stopped

    ports:
      - "127.0.0.1:8080:8080"

    env_file:
      - /opt/araripe-invest-api/application.env

    environment:
      SPRING_PROFILES_ACTIVE: production
      SERVER_PORT: 8080

    healthcheck:
      test:
        [
          "CMD-SHELL",
          "wget -q --spider http://localhost:8080/actuator/health || exit 1"
        ]
      interval: 30s
      timeout: 5s
      retries: 5
      start_period: 60s

    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "5"
```

---

# PARTE II — Terraform

## 5. Bootstrap do estado remoto

O backend remoto deve ser criado antes da infraestrutura principal.

### Recursos

- Bucket S3 para o estado do Terraform;
- versionamento do bucket;
- criptografia;
- bloqueio de acesso público;
- mecanismo de locking compatível com a versão utilizada;
- lifecycle para impedir exclusão acidental.

### Checklist

- [ ] Criar `infra/terraform/bootstrap`.
- [ ] Criar bucket S3 exclusivo para o state.
- [ ] Habilitar versionamento.
- [ ] Habilitar criptografia.
- [ ] Bloquear acesso público.
- [ ] Aplicar proteção contra exclusão acidental.
- [ ] Executar o bootstrap manualmente.
- [ ] Configurar o backend do ambiente de produção.
- [ ] Executar `terraform init -migrate-state`, quando necessário.
- [ ] Não armazenar o state dentro do Git.

---

## 6. Módulo de rede

### Recursos

- VPC;
- Internet Gateway;
- uma subnet pública para a EC2;
- duas subnets privadas em zonas diferentes para o RDS;
- route table pública;
- associação da route table pública;
- DB subnet group.

### Arquitetura de rede

```text
VPC
├── Availability Zone A
│   ├── Subnet pública
│   │   └── EC2
│   └── Subnet privada
│       └── elegível para RDS
└── Availability Zone B
    └── Subnet privada
        └── elegível para RDS
```

### Checklist

- [ ] Definir um CIDR para a VPC.
- [ ] Criar subnet pública na primeira AZ.
- [ ] Criar duas subnets privadas em AZs diferentes.
- [ ] Criar Internet Gateway.
- [ ] Criar rota `0.0.0.0/0` para o Internet Gateway apenas na subnet pública.
- [ ] Não criar rota direta para internet nas subnets privadas.
- [ ] Criar DB subnet group com as duas subnets privadas.
- [ ] Adicionar tags de projeto e ambiente.

> Sem NAT Gateway, o custo diminui. O RDS não precisa iniciar conexões para a internet para o funcionamento normal da aplicação.

---

## 7. Security Groups

## 7.1 Security Group da EC2

### Entrada

| Porta | Origem | Finalidade |
| --- | --- | --- |
| 80 | `0.0.0.0/0` | HTTP e desafio Certbot |
| 443 | `0.0.0.0/0` | HTTPS |
| 22 | nenhuma | SSH desabilitado inicialmente |

### Saída

- Permitir saída necessária para:
  - ECR;
  - Systems Manager;
  - APIs externas;
  - SMTP;
  - PostgreSQL;
  - atualizações de pacotes.

### Checklist

- [ ] Liberar porta 80.
- [ ] Liberar porta 443.
- [ ] Não liberar porta 8080.
- [ ] Não liberar porta 22 para a internet.
- [ ] Usar SSM para administração.
- [ ] Documentar qualquer exceção temporária de SSH.

---

## 7.2 Security Group do RDS

### Entrada

| Porta | Origem |
| --- | --- |
| 5432 | Security Group da EC2 |

### Checklist

- [ ] Permitir PostgreSQL somente a partir do Security Group da EC2.
- [ ] Não usar CIDR público.
- [ ] Não permitir `0.0.0.0/0`.
- [ ] Não tornar o RDS publicamente acessível.

---

## 8. RDS PostgreSQL básico

### Configuração inicial

```text
Engine: PostgreSQL
Classe: db.t4g.micro
Multi-AZ: false
Publicly accessible: false
Storage: 20 GiB gp3
Encryption: true
Backup retention: 7 dias
Deletion protection: true
Final snapshot: true
```

### Checklist

- [ ] Criar RDS PostgreSQL.
- [ ] Validar versão compatível com a aplicação e com Flyway.
- [ ] Configurar `db.t4g.micro`.
- [ ] Configurar Single-AZ.
- [ ] Configurar armazenamento gp3.
- [ ] Configurar 20 GiB inicialmente.
- [ ] Avaliar autoscaling de armazenamento com limite.
- [ ] Habilitar criptografia.
- [ ] Configurar backup de 7 dias.
- [ ] Definir janela de backup.
- [ ] Definir janela de manutenção.
- [ ] Habilitar deletion protection em produção.
- [ ] Exigir snapshot final na exclusão.
- [ ] Manter `publicly_accessible = false`.
- [ ] Associar ao DB subnet group privado.
- [ ] Associar ao Security Group do RDS.
- [ ] Gerar senha forte.
- [ ] Não registrar senha no código nem em `terraform.tfvars`.
- [ ] Criar output apenas para endpoint e porta.
- [ ] Marcar outputs sensíveis quando aplicável.

### Observação operacional

A configuração Single-AZ reduz custos, mas não fornece standby automático. Uma indisponibilidade da zona ou manutenção pode deixar o banco temporariamente indisponível.

---

## 9. ECR

### Checklist

- [ ] Criar repositório `araripe-invest-api`.
- [ ] Habilitar scan de imagem no push.
- [ ] Utilizar tags imutáveis.
- [ ] Configurar lifecycle para remover imagens antigas.
- [ ] Manter as últimas imagens suficientes para rollback.

### Estratégia de tags

```text
<git-sha>
production
```

A referência imutável do deploy deve ser o SHA:

```text
araripe-invest-api:<GITHUB_SHA>
```

A tag `production` pode ser mantida apenas como referência auxiliar.

---

## 10. IAM da EC2

### EC2 Instance Profile

A EC2 precisa de permissões para:

- registrar-se no Systems Manager;
- receber comandos do SSM;
- baixar imagens do ECR;
- ler parâmetros e segredos estritamente necessários;
- enviar logs, caso CloudWatch seja habilitado.

### Checklist

- [ ] Criar IAM role para EC2.
- [ ] Criar instance profile.
- [ ] Anexar permissões do Systems Manager.
- [ ] Permitir pull apenas do ECR necessário.
- [ ] Permitir leitura apenas dos parâmetros/secrets necessários.
- [ ] Não conceder `AdministratorAccess`.
- [ ] Associar o instance profile à EC2.

---

## 11. EC2

### Configuração inicial

- AMI Ubuntu LTS ou Amazon Linux compatível;
- uma única instância;
- subnet pública;
- Elastic IP;
- volume EBS criptografado;
- metadata service IMDSv2 obrigatório;
- IAM instance profile;
- Security Group da EC2.

### Checklist

- [ ] Escolher AMI suportada.
- [ ] Definir classe da EC2.
- [ ] Criar volume EBS criptografado.
- [ ] Configurar tamanho inicial do volume.
- [ ] Exigir IMDSv2.
- [ ] Associar Security Group.
- [ ] Associar instance profile.
- [ ] Associar Elastic IP.
- [ ] Habilitar termination protection, se desejado.
- [ ] Adicionar tags.
- [ ] Validar acesso pela Session Manager.
- [ ] Não expor SSH publicamente.

---

## 12. Route 53

### Ordem

O registro da API deve ser criado depois que o Elastic IP existir.

### Checklist

- [ ] Localizar a Hosted Zone do domínio.
- [ ] Criar registro `A`.
- [ ] Apontar `api.<DOMAIN_NAME>` para o Elastic IP.
- [ ] Definir TTL inicial baixo, por exemplo 300 segundos.
- [ ] Validar resolução DNS.

```bash
dig api.<DOMAIN_NAME>
```

- [ ] Confirmar que o IP retornado é o Elastic IP da EC2.

---

## 13. GitHub OIDC

### Objetivo

Permitir que o GitHub Actions assuma uma role temporária na AWS sem armazenar:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

### Roles recomendadas

Separar as responsabilidades:

```text
github-actions-ci
github-actions-deploy
github-actions-terraform-plan
github-actions-terraform-apply
```

Para uma primeira versão, é possível começar com:

```text
github-actions-deploy
github-actions-terraform
```

### Checklist

- [ ] Criar ou reutilizar o provider OIDC do GitHub na conta AWS.
- [ ] Criar role para deploy.
- [ ] Restringir a trust policy ao repositório:

```text
repo:freirelts/araripe-invest-api:*
```

- [ ] Restringir deploy de produção ao environment `production`.
- [ ] Restringir Terraform apply à branch `main` e ao environment de produção.
- [ ] Conceder somente permissões necessárias.
- [ ] Não conceder acesso administrativo irrestrito.
- [ ] Configurar GitHub Environment `production`.
- [ ] Configurar aprovação manual para produção, se desejado.

---

# PARTE III — Ansible e configuração da EC2

## 14. Estratégia de execução do Ansible

Há duas opções:

### Opção inicial

Executar o Ansible localmente pela Session Manager ou com acesso SSH temporário e restrito.

### Opção recomendada para evolução

Executar configuração por meio de:

- SSM State Manager;
- imagem preparada com Packer;
- runner controlado dentro da AWS.

Para a primeira subida, o Ansible pode ser executado uma vez para preparar a máquina.

---

## 15. Role base

### Checklist

- [ ] Atualizar pacotes.
- [ ] Instalar utilitários necessários.
- [ ] Configurar timezone.
- [ ] Criar usuário de aplicação.
- [ ] Criar diretório `/opt/araripe-invest-api`.
- [ ] Definir permissões do diretório.
- [ ] Configurar log rotation quando necessário.
- [ ] Garantir que SSM Agent esteja instalado e ativo.
- [ ] Evitar alterações manuais não documentadas.

---

## 16. Role Docker

### Checklist

- [ ] Instalar Docker Engine.
- [ ] Instalar Docker Compose Plugin.
- [ ] Habilitar serviço Docker.
- [ ] Iniciar serviço Docker.
- [ ] Adicionar usuário operacional ao grupo Docker, se necessário.
- [ ] Validar:

```bash
docker version
docker compose version
```

---

## 17. Role da aplicação

### Checklist

- [ ] Criar `/opt/araripe-invest-api`.
- [ ] Copiar `docker-compose.production.yml`.
- [ ] Criar `/opt/araripe-invest-api/application.env`.
- [ ] Definir permissões restritas no arquivo de ambiente.
- [ ] Criar scripts de deploy.
- [ ] Não armazenar segredos dentro do playbook.
- [ ] Obter segredos do Parameter Store ou Secrets Manager.
- [ ] Validar acesso ao ECR.
- [ ] Validar acesso ao endpoint do RDS.

---

## 18. Nginx

### Configuração inicial

Criar:

```text
/etc/nginx/sites-available/araripe-invest-api
```

Exemplo antes do Certbot:

```nginx
server {
    listen 80;
    listen [::]:80;

    server_name api.<DOMAIN_NAME>;

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 10s;
        proxy_send_timeout 120s;
        proxy_read_timeout 120s;
    }
}
```

### Checklist

- [ ] Instalar Nginx.
- [ ] Remover configuração default quando apropriado.
- [ ] Criar virtual host.
- [ ] Criar link em `sites-enabled`.
- [ ] Executar `nginx -t`.
- [ ] Recarregar Nginx.
- [ ] Validar resposta HTTP.
- [ ] Configurar limites e timeouts adequados às chamadas de IA.
- [ ] Não encaminhar headers inseguros sem necessidade.
- [ ] Ocultar informações excessivas da versão do servidor.

---

## 19. Certbot

### Pré-requisitos

Antes de emitir o certificado:

- EC2 ativa;
- Elastic IP associado;
- Route 53 apontando para o Elastic IP;
- DNS propagado;
- porta 80 liberada;
- Nginx respondendo por HTTP.

### Checklist

- [ ] Instalar Certbot.
- [ ] Instalar integração Certbot/Nginx.
- [ ] Executar:

```bash
sudo certbot --nginx \
  --domain api.<DOMAIN_NAME> \
  --email <CERTBOT_EMAIL> \
  --agree-tos \
  --non-interactive \
  --redirect
```

- [ ] Validar HTTPS.
- [ ] Validar redirecionamento HTTP para HTTPS.
- [ ] Executar teste de renovação:

```bash
sudo certbot renew --dry-run
```

- [ ] Verificar timer:

```bash
systemctl status certbot.timer
systemctl list-timers | grep certbot
```

- [ ] Não executar Certbot a cada deploy.
- [ ] Documentar procedimento de recuperação dos certificados.
- [ ] Não versionar `/etc/letsencrypt`.

---

# PARTE IV — Segredos e configuração

## 20. Estratégia de segredos

### Recomendação

Usar:

- AWS Systems Manager Parameter Store para valores de configuração;
- AWS Secrets Manager quando houver necessidade de rotação ou gestão específica;
- GitHub Secrets somente para segredos exclusivos do pipeline;
- GitHub Variables para identificadores não sensíveis.

### Exemplos no Parameter Store/Secrets Manager

```text
/araripe-invest-api/production/database/url
/araripe-invest-api/production/database/username
/araripe-invest-api/production/database/password
/araripe-invest-api/production/jwt/secret
/araripe-invest-api/production/brapi/token
/araripe-invest-api/production/openai/api-key
/araripe-invest-api/production/mail/username
/araripe-invest-api/production/mail/password
```

### Checklist

- [ ] Criar parâmetros por ambiente.
- [ ] Criptografar parâmetros sensíveis.
- [ ] Restringir leitura à IAM role da EC2.
- [ ] Não expor valores no Terraform output.
- [ ] Não salvar segredos no GitHub Variables.
- [ ] Não salvar segredos em arquivos versionados.
- [ ] Não imprimir o conteúdo do arquivo de ambiente nos logs do pipeline.
- [ ] Revisar logs da aplicação para evitar vazamento de tokens.

---

## 21. Bootstrap do administrador

### Procedimento recomendado

- [ ] Manter `ARARIPE_BOOTSTRAP_ADMIN_ENABLED=false` normalmente.
- [ ] Na primeira inicialização, habilitar temporariamente.
- [ ] Definir e-mail, nome e senha forte do primeiro administrador.
- [ ] Iniciar a aplicação.
- [ ] Validar a criação do administrador.
- [ ] Desabilitar imediatamente o bootstrap.
- [ ] Atualizar o arquivo de ambiente.
- [ ] Reiniciar a aplicação.
- [ ] Confirmar que o bootstrap não executa novamente.
- [ ] Trocar a senha inicial após o primeiro login, quando aplicável.

---

# PARTE V — CI com GitHub Actions

## 22. Workflow de CI

Criar:

```text
.github/workflows/ci.yml
```

### Gatilhos

- pull requests para `main`;
- pushes para branches relevantes.

### Etapas

- [ ] Checkout.
- [ ] Configurar Java 21.
- [ ] Habilitar cache Maven.
- [ ] Executar `./mvnw clean verify`.
- [ ] Executar testes com Testcontainers.
- [ ] Validar build do Dockerfile.
- [ ] Não publicar imagem em pull request.
- [ ] Não executar deploy em pull request.
- [ ] Definir timeout do job.
- [ ] Cancelar execuções anteriores da mesma branch quando apropriado.

### Exemplo

```yaml
name: CI

on:
  pull_request:
    branches: [main]
  push:
    branches:
      - main
      - "feat/**"
      - "fix/**"

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 20

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Validate Maven wrapper
        uses: gradle/actions/wrapper-validation@v4
        continue-on-error: true

      - name: Test
        run: ./mvnw clean verify -B

      - name: Validate Docker build
        run: docker build -t araripe-invest-api:ci .
```

> Revisar a action usada para validação do wrapper Maven. Não manter uma action específica de Gradle como solução definitiva.

---

# PARTE VI — CD com GitHub Actions

## 23. Workflow de deploy

Criar:

```text
.github/workflows/deploy-production.yml
```

### Gatilho recomendado

- push na branch `main`;
- opcionalmente, tags de release;
- execução manual com `workflow_dispatch`.

### Etapas

```text
Checkout
   |
   v
Testes
   |
   v
Autenticação OIDC
   |
   v
Login no ECR
   |
   v
Docker build
   |
   v
Docker push com SHA
   |
   v
SSM SendCommand
   |
   v
docker compose pull/up
   |
   v
Health check
```

### Checklist

- [ ] Configurar `id-token: write`.
- [ ] Configurar `contents: read`.
- [ ] Assumir role AWS por OIDC.
- [ ] Fazer login no ECR.
- [ ] Gerar tag usando `GITHUB_SHA`.
- [ ] Criar imagem.
- [ ] Publicar imagem.
- [ ] Enviar comando pelo SSM.
- [ ] Baixar a nova imagem na EC2.
- [ ] Atualizar `ARARIPE_IMAGE`.
- [ ] Executar `docker compose up -d`.
- [ ] Aguardar health check.
- [ ] Falhar o pipeline se o health check falhar.
- [ ] Executar rollback quando necessário.
- [ ] Remover imagens antigas com cautela.
- [ ] Preservar a imagem anterior para rollback.
- [ ] Proteger o environment `production`.

### Variáveis do GitHub Environment

```text
AWS_REGION
AWS_DEPLOY_ROLE_ARN
AWS_ACCOUNT_ID
ECR_REPOSITORY
EC2_INSTANCE_ID
PRODUCTION_API_URL
```

Esses valores não precisam ser secrets quando não forem sensíveis.

---

## 24. Script de deploy na EC2

Criar:

```text
deploy/scripts/deploy.sh
```

Responsabilidades:

- receber a URI imutável da imagem;
- salvar a imagem anterior;
- autenticar no ECR;
- baixar a nova imagem;
- atualizar o compose;
- iniciar o container;
- aguardar health check;
- restaurar a imagem anterior em caso de falha.

Exemplo de fluxo:

```bash
#!/usr/bin/env bash

set -Eeuo pipefail

APP_DIR="/opt/araripe-invest-api"
IMAGE_URI="${1:?Image URI is required}"

cd "$APP_DIR"

PREVIOUS_IMAGE="$(grep '^ARARIPE_IMAGE=' runtime.env 2>/dev/null | cut -d= -f2- || true)"

printf 'ARARIPE_IMAGE=%s\n' "$IMAGE_URI" > runtime.env

docker compose \
  --env-file runtime.env \
  -f docker-compose.production.yml \
  pull

docker compose \
  --env-file runtime.env \
  -f docker-compose.production.yml \
  up -d --remove-orphans

./health-check.sh
```

### Melhorias obrigatórias

- [ ] Implementar rollback real.
- [ ] Aplicar permissões restritas.
- [ ] Não imprimir segredos.
- [ ] Adicionar lock para evitar deploys simultâneos.
- [ ] Retornar código diferente de zero em falhas.

---

## 25. Health check pós-deploy

Criar:

```text
deploy/scripts/health-check.sh
```

### Endpoint interno

```text
http://127.0.0.1:8080/actuator/health
```

### Endpoint externo

```text
https://api.<DOMAIN_NAME>/actuator/health
```

### Checklist

- [ ] Aguardar período de inicialização.
- [ ] Tentar múltiplas vezes.
- [ ] Definir timeout.
- [ ] Validar status HTTP.
- [ ] Validar resposta `UP`.
- [ ] Não expor detalhes internos.
- [ ] Falhar o deploy quando a aplicação não ficar saudável.

---

## 26. Rollback

### Estratégia inicial

O rollback consiste em voltar para a imagem anterior no ECR.

### Checklist

- [ ] Registrar a imagem anterior antes do deploy.
- [ ] Não apagar a imagem anterior.
- [ ] Criar `rollback.sh`.
- [ ] Restaurar `ARARIPE_IMAGE`.
- [ ] Executar `docker compose up -d`.
- [ ] Validar health check.
- [ ] Documentar rollback manual.
- [ ] Testar rollback antes da primeira liberação oficial.

### Atenção às migrations

Uma migration destrutiva pode impedir rollback da aplicação.

- [ ] Usar migrations retrocompatíveis.
- [ ] Evitar renomear/remover coluna no mesmo deploy que altera o código.
- [ ] Aplicar estratégia expand-and-contract.
- [ ] Fazer backup antes de migrations críticas.
- [ ] Revisar migrations Flyway no pull request.

---

# PARTE VII — Pipeline do Terraform

## 27. Workflow Terraform

Criar:

```text
.github/workflows/terraform.yml
```

### Pull request

- `terraform fmt -check`;
- `terraform init`;
- `terraform validate`;
- `terraform plan`;
- publicar resumo do plan sem valores sensíveis.

### Branch `main`

- repetir validações;
- executar plan;
- aguardar aprovação do environment;
- executar apply do plan aprovado.

### Checklist

- [ ] Separar plan e apply.
- [ ] Usar OIDC.
- [ ] Usar role específica do Terraform.
- [ ] Proteger apply com environment.
- [ ] Restringir paths a `infra/terraform/**`.
- [ ] Não executar apply em pull requests.
- [ ] Salvar o plan como artifact quando necessário.
- [ ] Garantir que o apply utilize o plan correspondente.
- [ ] Configurar lock do state.
- [ ] Não imprimir variáveis sensíveis.
- [ ] Não usar `-auto-approve` fora de um environment protegido sem decisão explícita.

---

# PARTE VIII — Primeira subida

## 28. Ordem de execução

## Etapa 1 — Preparar aplicação

- [ ] Criar Dockerfile.
- [ ] Criar `.dockerignore`.
- [ ] Criar profile de produção.
- [ ] Criar compose de produção.
- [ ] Criar scripts de deploy e health check.
- [ ] Executar testes.
- [ ] Validar imagem local.

## Etapa 2 — Preparar Terraform

- [ ] Criar bootstrap do backend.
- [ ] Criar módulos.
- [ ] Criar ambiente `production`.
- [ ] Executar `terraform fmt`.
- [ ] Executar `terraform validate`.
- [ ] Executar `terraform plan`.

## Etapa 3 — Criar infraestrutura base

- [ ] Criar VPC.
- [ ] Criar subnets.
- [ ] Criar Internet Gateway.
- [ ] Criar route tables.
- [ ] Criar Security Groups.
- [ ] Criar DB subnet group.
- [ ] Criar ECR.
- [ ] Criar IAM da EC2.
- [ ] Criar RDS.
- [ ] Criar EC2.
- [ ] Criar Elastic IP.
- [ ] Associar Elastic IP.
- [ ] Criar registro Route 53.
- [ ] Criar IAM OIDC do GitHub.

## Etapa 4 — Configurar EC2

- [ ] Validar Session Manager.
- [ ] Executar Ansible.
- [ ] Instalar Docker.
- [ ] Instalar Nginx.
- [ ] Instalar Certbot.
- [ ] Criar diretórios.
- [ ] Criar arquivo de ambiente.
- [ ] Configurar acesso ao ECR.
- [ ] Configurar compose de produção.

## Etapa 5 — Validar DNS e HTTP

- [ ] Validar resolução DNS.
- [ ] Validar porta 80.
- [ ] Validar Nginx.
- [ ] Confirmar que a página ou resposta HTTP chega à EC2.

## Etapa 6 — Emitir certificado

- [ ] Executar Certbot.
- [ ] Validar HTTPS.
- [ ] Validar redirect HTTP → HTTPS.
- [ ] Executar `certbot renew --dry-run`.

## Etapa 7 — Configurar segredos

- [ ] Criar parâmetros e secrets na AWS.
- [ ] Conceder leitura à role da EC2.
- [ ] Gerar `application.env`.
- [ ] Restringir permissões.
- [ ] Validar variáveis obrigatórias.

## Etapa 8 — Primeiro deploy

- [ ] Executar workflow manualmente.
- [ ] Publicar imagem no ECR.
- [ ] Enviar deploy por SSM.
- [ ] Executar migrations Flyway.
- [ ] Validar container.
- [ ] Validar health check interno.
- [ ] Validar health check externo.
- [ ] Validar logs.

## Etapa 9 — Bootstrap do administrador

- [ ] Habilitar bootstrap temporariamente.
- [ ] Criar administrador.
- [ ] Desabilitar bootstrap.
- [ ] Reiniciar aplicação.
- [ ] Validar login.

## Etapa 10 — Testes funcionais

- [ ] Testar login.
- [ ] Testar endpoint autenticado.
- [ ] Testar CORS com o frontend.
- [ ] Testar integração brapi.
- [ ] Testar integração OpenAI.
- [ ] Testar envio de e-mail.
- [ ] Testar jobs agendados.
- [ ] Validar Flyway.
- [ ] Validar persistência no RDS.
- [ ] Reiniciar o container e validar recuperação.
- [ ] Reiniciar a EC2 e validar inicialização automática.

---

# PARTE IX — Segurança

## 29. Checklist de segurança

- [ ] RDS privado.
- [ ] RDS aceita conexão somente da EC2.
- [ ] Porta 8080 não exposta.
- [ ] Porta 22 não exposta.
- [ ] HTTPS obrigatório.
- [ ] Certificado renovável automaticamente.
- [ ] IMDSv2 obrigatório.
- [ ] EBS criptografado.
- [ ] RDS criptografado.
- [ ] Segredos fora do Git.
- [ ] Segredos fora da imagem.
- [ ] GitHub Actions autenticado via OIDC.
- [ ] IAM com menor privilégio.
- [ ] JWT secret com pelo menos 32 bytes fortes.
- [ ] CORS restrito ao frontend oficial.
- [ ] Actuator limitado a `health` e `info`.
- [ ] Health check sem detalhes sensíveis.
- [ ] Logs sem tokens, senhas ou payloads sensíveis.
- [ ] Proteção contra exclusão do RDS.
- [ ] Snapshot final do RDS.
- [ ] Backups habilitados.
- [ ] Scan de imagens no ECR.
- [ ] Dependências verificadas no CI.

---

# PARTE X — Observabilidade e operação

## 30. Logs iniciais

Para reduzir custo inicialmente:

- logs da aplicação via Docker;
- rotação pelo driver `json-file`;
- inspeção pelo SSM Session Manager.

### Checklist

- [ ] Configurar limite de tamanho dos logs Docker.
- [ ] Configurar quantidade máxima de arquivos.
- [ ] Padronizar logs da aplicação.
- [ ] Incluir correlation ID quando aplicável.
- [ ] Não registrar segredos.
- [ ] Documentar comandos de diagnóstico.

```bash
docker compose \
  -f /opt/araripe-invest-api/docker-compose.production.yml \
  logs --tail=200 -f api
```

### Evolução

- CloudWatch Agent;
- métricas do Actuator;
- Prometheus;
- Grafana;
- alertas de indisponibilidade;
- alertas de CPU, memória, disco e conexões do RDS.

---

## 31. Monitoramento mínimo

- [ ] Criar alarme de CPU alta da EC2.
- [ ] Criar alarme de status check da EC2.
- [ ] Criar alarme de espaço em disco.
- [ ] Criar alarme de CPU alta do RDS.
- [ ] Criar alarme de armazenamento do RDS.
- [ ] Criar alarme de conexões do RDS.
- [ ] Criar verificação externa do endpoint `/actuator/health`.
- [ ] Definir canal de notificação.

---

# PARTE XI — Backup e recuperação

## 32. RDS

- [ ] Confirmar backup automático.
- [ ] Confirmar retenção de 7 dias.
- [ ] Confirmar janela de backup.
- [ ] Criar snapshot manual antes da primeira produção.
- [ ] Criar snapshot antes de migrations críticas.
- [ ] Testar restauração em ambiente separado.
- [ ] Documentar RTO e RPO aceitos.

## 33. EC2

A EC2 deve ser tratada como substituível.

- [ ] Manter toda configuração no Ansible.
- [ ] Manter compose e scripts no repositório.
- [ ] Manter segredos na AWS.
- [ ] Não depender de arquivos criados manualmente.
- [ ] Documentar recriação completa da instância.
- [ ] Avaliar snapshots do volume somente quando necessário.

---

# PARTE XII — Critérios de conclusão

## 34. Definition of Done da infraestrutura

A primeira subida será considerada concluída quando:

- [ ] `https://api.<DOMAIN_NAME>/actuator/health` responder `UP`.
- [ ] HTTP redirecionar para HTTPS.
- [ ] Certificado for válido.
- [ ] Renovação do Certbot passar no `dry-run`.
- [ ] API conectar ao RDS privado.
- [ ] Flyway aplicar todas as migrations.
- [ ] RDS não estiver publicamente acessível.
- [ ] Porta 8080 não estiver acessível externamente.
- [ ] Porta 22 não estiver aberta para a internet.
- [ ] Deploy ocorrer pelo GitHub Actions.
- [ ] GitHub Actions usar OIDC.
- [ ] Deploy ocorrer por SSM.
- [ ] Rollback tiver sido testado.
- [ ] Backup do RDS estiver ativo.
- [ ] Restart da EC2 recuperar a aplicação automaticamente.
- [ ] CORS permitir somente os domínios esperados.
- [ ] Segredos não estiverem no Git, imagem ou logs.
- [ ] Documentação operacional estiver atualizada.

---

# PARTE XIII — Próximas evoluções

Após estabilizar a primeira versão:

- [ ] Criar ambiente de homologação.
- [ ] Separar conta ou VPC por ambiente.
- [ ] Adicionar CloudWatch Agent.
- [ ] Adicionar dashboards e alertas.
- [ ] Adicionar análise de dependências.
- [ ] Adicionar scan de vulnerabilidades.
- [ ] Adicionar WAF ou Cloudflare, se necessário.
- [ ] Avaliar RDS `db.t4g.small`.
- [ ] Avaliar Multi-AZ.
- [ ] Avaliar segunda EC2.
- [ ] Avaliar ALB.
- [ ] Avaliar ACM.
- [ ] Avaliar Auto Scaling.
- [ ] Avaliar ECS.
- [ ] Avaliar blue-green ou rolling deployment.
- [ ] Avaliar Packer para imagem imutável.
- [ ] Avaliar OpenTelemetry.
- [ ] Avaliar Prometheus e Grafana.

---

# PARTE XIV — Comandos úteis

## Terraform

```bash
cd infra/terraform/environments/production

terraform fmt -recursive
terraform init
terraform validate
terraform plan
terraform apply
```

## Ansible

```bash
cd infra/ansible

ansible-playbook \
  -i inventories/production \
  site.yml
```

## Docker na EC2

```bash
cd /opt/araripe-invest-api

docker compose \
  --env-file runtime.env \
  -f docker-compose.production.yml \
  ps

docker compose \
  --env-file runtime.env \
  -f docker-compose.production.yml \
  logs --tail=200 -f api
```

## Nginx

```bash
sudo nginx -t
sudo systemctl status nginx
sudo systemctl reload nginx
```

## Certbot

```bash
sudo certbot certificates
sudo certbot renew --dry-run
sudo systemctl status certbot.timer
```

## Aplicação

```bash
curl -i http://127.0.0.1:8080/actuator/health
curl -i https://api.<DOMAIN_NAME>/actuator/health
```

## RDS

```bash
nc -vz <RDS_ENDPOINT> 5432
```

---

# PARTE XV — Ordem sugerida dos pull requests

Para reduzir risco e facilitar revisão:

1. `feat: add production Docker image`
2. `feat: add production Spring profile`
3. `feat: add production compose and deployment scripts`
4. `feat: add Terraform bootstrap and network`
5. `feat: add RDS and security groups`
6. `feat: add EC2, ECR and IAM`
7. `feat: add Route 53 and GitHub OIDC`
8. `feat: add Ansible server configuration`
9. `ci: add validation workflow`
10. `ci: add production deployment workflow`
11. `ci: add Terraform workflow`
12. `docs: document production operations and recovery`

---

## Resultado esperado

Ao final, o processo normal de publicação será:

```text
Merge na main
    |
    v
GitHub Actions
    |
    ├── testes
    ├── build
    ├── imagem Docker
    ├── push ECR
    └── deploy via SSM
             |
             v
           EC2
             |
             ├── Docker Compose
             ├── Nginx
             └── HTTPS com Certbot
                     |
                     v
                 RDS privado
```
