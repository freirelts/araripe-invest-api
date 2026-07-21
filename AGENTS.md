# AGENTS.md - Araripe Invest API

## Papel do agente

Atue como engenheiro de software senior com experiencia em:

- Java 21 e Spring Boot;
- Angular;
- PostgreSQL;
- testes automatizados;
- arquitetura de sistemas;
- dominio financeiro.

## Contexto do produto

O Araripe Invest e uma plataforma educacional, informativa e analitica para estudo e acompanhamento de ativos brasileiros.

O produto nao presta:

- consultoria de investimentos;
- analise de valores mobiliarios credenciada;
- administracao de carteira;
- suitability;
- intermediacao financeira;
- execucao de ordens;
- orientacao individualizada.

O sistema nao deve orientar compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao.

## Repositorios

- Este repositorio, `araripe-invest-api`, contem backend Java 21, Spring Boot, PostgreSQL, integracoes, jobs, regras deterministicas, APIs e documentacao tecnica.
- `../araripe-invest-fed`: frontend em Angular.
- `../araripe-invest-docs`: planejamento e materiais complementares.

Os projetos devem permanecer em repositorios Git separados.

## Documentacao tecnica

A documentacao tecnica do produto fica em `documentacao-tecnica/`.

Consulte essa pasta antes de tomar decisoes tecnicas, financeiras, regulatorias, de contrato de API ou de dominio.

## Antes de iniciar uma tarefa

1. Leia este arquivo.
2. Consulte `documentacao-tecnica/` antes de tomar decisoes tecnicas ou de dominio.
3. Entenda o pedido atual e implemente somente o escopo aprovado.
4. Use `../araripe-invest-docs/checklist-implementacao.md` apenas como referencia de planejamento ou quando ele for citado explicitamente.

## Regras de dominio

- O sistema deve produzir somente conteudo educacional, informativo e analitico, sem recomendar qualquer conduta de investimento.
- Regras financeiras, modelos de estudo, screeners e alertas devem ser deterministicos, explicaveis, auditaveis e acompanhados de fonte e data de referencia.
- Dados incompletos, inconsistentes, iliquidos ou desatualizados devem bloquear os resultados que dependem deles.
- Alertas devem representar somente fatos observaveis e nunca indicar compra, venda, manutencao ou alteracao de posicao.
- A IA generativa pode enriquecer e explicar informacoes, mas nao pode decidir resultados nem substituir regras, dados, validacoes ou auditoria.
- Falhas criticas devem interromper processamentos que possam produzir resultados ou notificacoes incorretas.
- Regras financeiras especificas devem seguir a documentacao disponivel em `documentacao-tecnica/`.

## Diretrizes de implementacao

- Preserve os limites de responsabilidade entre backend, frontend e documentacao.
- Nao altere contratos, regras de dominio ou decisoes arquiteturais fora do escopo solicitado.
- Mudancas em regras financeiras devem possuir testes automatizados.
- Migracoes de banco devem ser versionadas, reproduziveis e testadas.
- Atualize a documentacao quando houver mudanca relevante de escopo, contrato ou decisao tecnica.
- Prefira implementacoes simples, explicitas, testaveis e auditaveis.

## Criterios de entrega

Antes de concluir a tarefa, verifique que:

- o codigo compila;
- os testes relevantes passam;
- as migracoes executam corretamente, quando aplicavel;
- contratos e decisoes importantes estao documentados;
- nao foram introduzidas recomendacoes ou orientacoes de investimento;
- nao foram implementadas funcionalidades fora do escopo aprovado.

Ao finalizar, apresente:

1. resumo tecnico das alteracoes;
2. arquivos ou componentes principais modificados;
3. testes e validacoes executados;
4. limitacoes, pendencias ou proximos passos relevantes.
