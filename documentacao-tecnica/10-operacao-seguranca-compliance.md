# Operação, segurança e compliance

## Operação

O MVP deve operar com job diário após o fechamento do mercado. Para acompanhamento educacional de ativos, a pressão operacional é menor que em estratégias de curto prazo, mas a qualidade dos dados fundamentalistas, a rastreabilidade das premissas e a auditoria das alertas informativos são mais importantes.

Além da execução agendada, o backend deve permitir execução manual por endpoint administrativo para reprocessamento controlado, troubleshooting e recuperação de falha parcial. A execução manual deve:

- exigir perfil `ADMIN`;
- receber data de referência explicitamente;
- registrar usuário solicitante, origem manual, parâmetros, início, fim, status e erro resumido;
- usar as mesmas regras de idempotência da execução agendada;
- não duplicar candles, indicadores, modelos de estudo, alertas informativos ou e-mails;
- bloquear ou rejeitar nova execução conflitante quando já houver execução em andamento para o mesmo job e data de referência.

O fluxo operacional diário deve respeitar dependência estrita entre etapas. Se uma etapa crítica falhar, as etapas seguintes não devem executar com dados possivelmente antigos.

Ordem crítica:

1. coleta diária de mercado, fundamentos, demonstrativos, dividendos e macro;
2. cálculo de indicadores técnicos e fundamentalistas;
3. filtros e modelos de estudo;
4. screener;
5. varredura de registros informativos de posição;
6. digest diário de e-mails.

Se coleta, indicadores, filtros/modelos de estudo, screener ou varredura de acompanhamentos falharem, o fluxo deve parar e registrar `FAILED`. A publicação de digest não deve ser executada depois de uma falha de pré-requisito, para evitar e-mail baseado em alerta informativo antigo ou incompleto. O job de digest executado isoladamente pode retornar `SKIPPED` quando não houver alertas informativos.

## Observabilidade

O `araripe-invest-api` deve expor:

- health check;
- métricas de jobs;
- métricas de integração externa;
- quantidade de ativos processados;
- quantidade de modelos de estudo geradas;
- quantidade de posições abertas sem modelo de estudo acompanhado;
- quantidade de alertas informativos por posição;
- quantidade de e-mails diários consolidados via Spring Mail enviados, pendentes e com erro;
- latência, taxa de erro e quantidade de análises econômicas solicitadas via OpenAI;
- quantidade de ativos eliminados por filtro;
- erros por fonte;
- idade dos dados fundamentalistas por ativo;
- quantidade de endpoints da brapi com falha por ausência de token ou permissão.

Ferramentas indicadas:

- Spring Actuator;
- logs estruturados;
- Prometheus e Grafana em fase futura.

## Logs

Logs devem incluir:

- `correlationId`;
- nome do job;
- endpoint e usuário solicitante, quando a operação for acionada por API;
- data de referência;
- ativo;
- fonte;
- status;
- duração;
- mensagem de erro resumida.
- modelo de IA e versão do prompt, quando aplicável;
- tipo de alerta informativo, lote diário de e-mail e item de alerta enviado, quando aplicável.

Logs não devem incluir:

- tokens;
- chaves de API;
- dados sensíveis de usuário;
- payloads completos quando contiverem credenciais.
- prompts com dados sensíveis desnecessários;
- destino completo de notificação quando mascaramento for adequado.

## Segurança

MVP local:

- variáveis de ambiente;
- secrets fora do código;
- CORS restrito ao frontend local;
- tokens em `.env` local não versionado;
- login funcionando e criação administrativa de usuário com senha protegida por hash.
- chaves OpenAI e credenciais de e-mail/SMTP fora do código.

Ambiente publicado:

- autenticação;
- autorização;
- controle de perfil `CUSTOMER` e `ADMIN`;
- HTTPS;
- rate limit;
- rotação de tokens;
- backups do PostgreSQL;
- política de retenção.
- criptografia ou mascaramento para dados sensíveis de usuário;
- controle de acesso a prompts, respostas de IA e eventos de notificação.

## Controle de acesso

Perfis:

- `CUSTOMER`: usuário comum assinante da plataforma. Acessa dashboards, screener, detalhes de modelos de estudo, registros informativos de posição, alertas informativos e histórico.
- `ADMIN`: acessa tudo que o assinante acessa e também configurações gerais, cadastro de usuários e cadastro dos ativos do universo monitorado.

Regras:

- rotas administrativas devem exigir perfil `ADMIN`;
- usuário não autenticado deve acessar apenas landing page e login;
- usuário `CUSTOMER` deve ter status de acesso válido para consumir a plataforma autenticada;
- no MVP, o status de assinatura pode ser controlado administrativamente, sem integração obrigatória com gateway de pagamento;
- senha nunca deve ser armazenada em texto puro;
- tokens não devem ser registrados em logs;
- token da brapi.dev deve ser usado somente no backend;
- chaves OpenAI e credenciais de e-mail/SMTP devem ser usadas somente no backend;
- remetente e credenciais SMTP devem ser configurados por ambiente e nunca versionados;
- o sistema deve enviar no máximo um e-mail diário por cliente, apenas quando houver alertas informativos em `informational_alerts`;
- criação de usuário deve ser restrita ao perfil `ADMIN`;
- bloqueio de usuário deve revogar acesso na próxima autenticação ou validação de token, conforme estratégia escolhida.

## Compliance e linguagem

O Araripe Invest deve ser tratado como ferramenta de alerta informativo operacional de acompanhamento educacional de ativos com risco explícito. A comunicação deve deixar claro que:

- mercado financeiro envolve risco;
- resultados passados não garantem resultados futuros;
- alertas informativos são gerados a partir dos dados cadastrados, limiares definidos pelo usuário, regras do sistema e fontes disponíveis;
- o sistema não executa ordens;
- o sistema não presta consultoria de valores mobiliários, análise credenciada, administração de carteira, suitability ou intermediação;
- o sistema não produz recomendação individualizada;
- o sistema não promete retorno, segurança, ausência de risco ou decisão automatizada de investimento;
- fundamentos podem mudar;
- valuation é estimativa, não certeza;
- dividendos podem ser reduzidos ou suspensos;
- o usuário deve manter registros informativos, limiares de preço definidos pelo usuário e preferências de alerta atualizados;
- o usuário pode associar um modelo de estudo acompanhado aos ativos que deseja acompanhar com alertas informativos completas;
- acompanhamento educacional de ativos exige acompanhamento periódico, ainda que menos frequente que estratégias de curto prazo.
- IA generativa pode errar, omitir contexto ou interpretar fontes de forma incompleta; por isso a alerta informativo final deve ser validada por regras determinísticas.

## Textos indicados

Aviso curto:

```text
Alerta informativo de acompanhamento educacional de ativos baseada nos dados cadastrados e nas regras do Araripe Invest. Operações em renda variável envolvem risco e não há garantia de retorno.
```

Aviso completo:

```text
O Araripe Invest gera alertas informativos de acompanhamento educacional de ativos com base em regras objetivas, dados de mercado, fundamentos, valuation educacional, registros informativos declarados pelo usuário, limiares definidos pelo usuário, modelos de estudo acompanhados e contexto econômico enriquecido por IA quando disponível. Os alertas informativos não garantem retorno, não eliminam risco, não representam recomendação individualizada e não executam ordens em corretora. Mantenha seus dados atualizados e consulte profissionais habilitados quando necessário.
```

Termos atuais:

```text
Versão: terms-educational-v1.
O Araripe Invest oferece conteúdo educacional, informativo e analítico para estudo e acompanhamento de ativos brasileiros. O produto não presta consultoria de valores mobiliários, análise credenciada, administração de carteira, suitability, intermediação ou execução de ordens. Não há recomendação individualizada, promessa de retorno, promessa de segurança, decisão automatizada de investimento ou comando de compra, venda, manutenção, aumento, redução, alocação ou encerramento.
```

## Riscos do produto

| Risco                                               | Mitigação                                                                                                                                                         |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dados errados gerarem modelo de estudo              | Validação de qualidade, fonte registrada e bloqueio por inconsistência.                                                                                           |
| Usuário seguir alerta informativo sem avaliar risco | Avisos claros, explicação de risco, limiar inferior de preço definido pelo usuário, limiar superior de preço definido pelo usuário e responsabilidade do usuário. |
| Valuation mal calibrado                             | Margem de segurança, comparação histórica/setorial e explicabilidade.                                                                                             |
| Dividendo alto ser armadilha                        | Validar lucro, caixa, payout e recorrência.                                                                                                                       |
| Baixa liquidez causar execução ruim                 | Filtro mínimo de volume financeiro.                                                                                                                               |
| Backtest enviesado                                  | Evitar dados futuros e documentar ajustes de preço.                                                                                                               |
| Excesso de confiança em score                       | Mostrar filtros, fundamentos, valuation e risco junto da nota.                                                                                                    |
| Falha de API externa                                | Retry limitado, timeout, bloqueio de regra dependente e status de coleta.                                                                                         |
| IA gerar contexto incorreto                         | Execução sob demanda, saída estruturada, validação, fontes registradas, fallback determinístico e auditoria.                                                      |
| Envio de e-mail duplicar alerta                     | E-mail diário consolidado e idempotência por usuário, data, canal e versão da regra.                                                                              |
| Dados de posição declarada desatualizados           | Interface de manutenção, aviso de atualização e uso explícito da data do cadastro.                                                                                |
| Posição sem modelo de estudo acompanhado            | Exigir associação para alertas informativos completas e limitar saída a reavaliação pendente.                                                                     |
| Demonstrativo antigo parecer fresco                 | Validar frescor por período contábil (`most_recent_quarter`) e prazo esperado do próximo demonstrativo.                                                           |
| Crescimento distorcido por base negativa            | Não usar percentual tradicional quando a base anterior for zero ou negativa.                                                                                      |
| FCF negativo falso por snapshots repetidos          | Contar períodos contábeis distintos, não snapshots diários derivados.                                                                                             |
