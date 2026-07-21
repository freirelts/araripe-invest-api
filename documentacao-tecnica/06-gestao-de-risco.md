# Risco Analítico E Limiares Informativos

## Propósito

No Araripe Invest, risco é tratado como material analítico e educacional. O sistema calcula indicadores, faixas de observação e limiares informativos para explicar cenários, mas não usa esses dados para orientar compra, venda, manutenção, aumento, redução, alocação ou encerramento de posição.

## Cálculos Permitidos

### Preço De Referência Do Estudo

```text
preco_referencia_estudo = preco_justo_estimado * (1 - margem_de_seguranca)
```

Exemplo:

```text
preco_justo_estimado = R$ 50,00
margem_de_seguranca = 20%
preco_referencia_estudo = R$ 40,00
```

### Diferença Para O Preço De Referência

```text
diferenca_percentual = (preco_referencia_estudo - preco_atual) / preco_atual
```

Essa métrica deve ser exibida como comparação educacional, sem conclusão operacional.

### Limiar Inferior De Preço Definido Pelo Usuário

```text
preco_limiar_inferior = valor_informado_pelo_usuario
```

O sistema pode avisar que o preço cruzou o limiar. O texto do alerta deve indicar apenas o fato observado.

### Limiar Superior De Preço Definido Pelo Usuário

```text
preco_limiar_superior = valor_informado_pelo_usuario
```

O sistema pode avisar que o preço cruzou o limiar. O texto do alerta não deve sugerir realização, venda, redução, aumento ou qualquer conduta.

### Exposição Informativa

Quando o usuário cadastrar posição real, o sistema pode calcular exposição apenas para visualização:

```text
valor_posicao = quantidade * preco_atual
percentual_no_cadastro = valor_posicao / valor_total_informado
```

Esse cálculo não pode virar quantidade, aporte, alocação individualizada ou instrução.

## Regras Obrigatórias

1. Nunca gerar modelo de estudo sem ponto de reavaliação.
2. Nunca transformar valuation, margem de segurança ou score em comando operacional.
3. Nunca sugerir quantidade, aporte ou alocação individualizada.
4. Nunca usar capital, exposição, setor ou perfil do usuário para indicar conduta.
5. Bloquear estudo quando dados estiverem incompletos, desatualizados ou inconsistentes.
6. Registrar todos os cálculos, fontes, datas e versões de regra para auditoria.
7. Notificar somente eventos objetivos: limiar atingido, dado atualizado, dado desatualizado, indicador fora de intervalo, premissa alterada ou bloqueio de qualidade.
8. Separar alerta informativo de execução: o sistema não envia ordem para corretora e não diz ao usuário o que fazer.

## Exemplo De Saída Informativa

```json
{
  "currentPrice": 25.0,
  "fairPriceEstimate": 32.0,
  "safetyMarginPercent": 15.0,
  "studyReferencePrice": 27.2,
  "lowerUserPriceThreshold": 21.25,
  "upperUserPriceThreshold": 31.25,
  "estimatedDifferencePercent": 8.8,
  "reviewPoint": "Reavaliar o estudo se a margem liquida deteriorar por dois periodos ou se o dado fundamentalista ficar desatualizado.",
  "eventType": "PRICE_THRESHOLD_REACHED",
  "status": "INFORMATIVE"
}
```

## Texto Para Interface

Exemplos permitidos:

- "O preço cruzou o limiar inferior definido por você."
- "O preço cruzou o limiar superior definido por você."
- "A premissa de margem líquida mudou em relação ao estudo anterior."
- "O dado fundamentalista usado no estudo está desatualizado."
- "A diferença entre o preço atual e o preço de referência do estudo mudou."

Exemplos proibidos:

- "Compre."
- "Venda."
- "Aumente posição."
- "Reduza posição."
- "Execute o limite inferior."
- "Realize o alvo."
- "Mantenha a posição."

## Eventos Notificáveis

Devem entrar no e-mail diário consolidado do cliente via Spring Mail:

- limiar inferior de preço atingido;
- limiar superior de preço atingido;
- dado atualizado;
- dado desatualizado;
- indicador fora do intervalo do estudo;
- premissa do estudo alterada;
- qualidade de dados bloqueada.

Cada item do resumo deve registrar usuário, ativo, evento, data de referência, fonte e versão da regra.
