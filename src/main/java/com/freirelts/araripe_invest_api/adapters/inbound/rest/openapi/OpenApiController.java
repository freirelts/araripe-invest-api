package com.freirelts.araripe_invest_api.adapters.inbound.rest.openapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
class OpenApiController {

	@GetMapping("/v3/api-docs")
	Map<String, Object> openApi() {
		return Map.of(
				"openapi", "3.0.3",
				"info", Map.of("title", "Araripe Invest API", "version", "v1"),
				"paths", paths(),
				"components", Map.of("securitySchemes",
						Map.of("bearerAuth", Map.of("type", "http", "scheme", "bearer", "bearerFormat", "JWT"))));
	}

	private Map<String, Object> paths() {
		Map<String, Object> paths = new LinkedHashMap<>();
		add(paths, "/api/v1/auth/login", "post", "Login com JWT assinado.");
		add(paths, "/api/v1/auth/me", "get", "Usuario autenticado.");
		add(paths, "/api/v1/legal/terms/current", "get", "Termos atuais de uso educacional e informativo.");
		add(paths, "/api/v1/screener", "get",
				"Screener de modelos de estudo ordenavel por criterio escolhido pelo usuario.");
		add(paths, "/api/v1/asset-studies/{studyId}", "get",
				"Detalhe do modelo de estudo com filtros, aderencia a criterios, fundamentos, referencias analiticas e contexto de IA validado quando existir.");
		add(paths, "/api/v1/asset-studies/history", "get", "Historico de modelos de estudo por ativo.");
		add(paths, "/api/v1/assets", "get", "Ativos monitorados ativos.");
		add(paths, "/api/v1/assets/{symbol}/fundamentals", "get",
				"Fundamentos, indicadores, demonstrativos e dividendos.");
		add(paths, "/api/v1/assets/{symbol}/diagnostics", "get", "Diagnostico de filtros eliminatorios.");
		add(paths, "/api/v1/watched-assets", "get", "Ativos acompanhados pelo usuario para alertas informativos.");
		add(paths, "/api/v1/watched-assets", "post",
				"Cadastra ou atualiza ativo acompanhado com limiares informativos definidos pelo usuario.");
		add(paths, "/api/v1/watched-assets/{watchItemId}", "put",
				"Atualiza preferencias de alerta de ativo acompanhado, sem inferir suitability.");
		add(paths, "/api/v1/watched-assets/{watchItemId}/archive", "patch", "Arquiva ativo acompanhado.");
		add(paths, "/api/v1/position-records", "get",
				"Registros informativos de posicao real abertos declarados pelo usuario.");
		add(paths, "/api/v1/position-records/history", "get",
				"Historico de registros informativos de posicao real encerrados pelo usuario.");
		add(paths, "/api/v1/position-records", "post", "Cria cadastro informativo de posicao real do usuario.");
		add(paths, "/api/v1/position-records/{positionId}", "put", "Edita cadastro informativo de posicao real.");
		add(paths, "/api/v1/position-records/{positionId}/quantity-adjustments", "post",
				"Atualiza quantidade e preco medio informados pelo usuario para fins de registro.");
		add(paths, "/api/v1/position-records/{positionId}/close", "patch",
				"Arquiva cadastro informativo de posicao real mantendo historico.");
		add(paths, "/api/v1/position-records/{positionId}/main-thesis", "post",
				"Associa modelo de estudo acompanhado.");
		add(paths, "/api/v1/position-records/{positionId}/main-thesis", "patch",
				"Troca modelo de estudo acompanhado.");
		add(paths, "/api/v1/alerts", "get", "Alertas informativos factuais dos ativos acompanhados.");
		add(paths, "/api/v1/alerts/{alertId}", "get", "Detalhe rastreavel de alerta informativo factual.");
		add(paths, "/api/v1/alerts/{alertId}/read", "patch", "Marca alerta informativo como lido na web.");
		add(paths, "/api/v1/ai/context-analyses", "get", "Lista analises economicas de IA persistidas.");
		add(paths, "/api/v1/ai/context-analyses/{analysisId}", "get", "Detalhe e status da analise economica de IA.");
		add(paths, "/api/v1/jobs/status", "get", "Status de coleta e jobs por data.");
		add(paths, "/api/v1/admin/jobs/{jobName}/runs", "post", "Execucao manual de job ou fluxo operacional.");
		add(paths, "/api/v1/admin/ai/context-analyses", "post", "Solicita analise economica de IA a partir de uma tese.");
		add(paths, "/api/v1/admin/users", "get", "Administracao de usuarios.");
		add(paths, "/api/v1/admin/users", "post", "Cadastro administrativo de usuario.");
		add(paths, "/api/v1/admin/users/{userId}", "put", "Edicao administrativa de usuario.");
		add(paths, "/api/v1/admin/users/{userId}/status", "patch", "Status de acesso e assinatura do usuario.");
		add(paths, "/api/v1/admin/assets", "get", "Administracao de ativos monitorados.");
		add(paths, "/api/v1/admin/assets", "post", "Cadastro administrativo de ativo.");
		add(paths, "/api/v1/admin/assets/{assetId}", "put", "Edicao administrativa de ativo.");
		add(paths, "/api/v1/admin/assets/{assetId}/status", "patch", "Ativacao ou inativacao de ativo.");
		return paths;
	}

	@SuppressWarnings("unchecked")
	private static void add(Map<String, Object> paths, String path, String method, String summary) {
		Map<String, Object> operations = (Map<String, Object>) paths.computeIfAbsent(path, ignored -> new LinkedHashMap<>());
		operations.put(method, Map.of(
				"summary", summary,
				"description", "Contrato REST da Fase 10 com DTO estavel, exemplos e erros padronizados.",
				"security", List.of(Map.of("bearerAuth", List.of())),
				"responses", Map.of("200", Map.of("description", "OK",
								"content", Map.of("application/json",
										Map.of("examples", Map.of("default",
												Map.of("summary", "Exemplo", "value", example(path)))))),
						"401", Map.of("description", "JWT ausente ou invalido"),
						"403", Map.of("description", "Perfil sem permissao"))));
	}

	private static Map<String, Object> example(String path) {
		if (path.contains("diagnostics")) {
			return Map.of("symbol", "WEGE3", "status", "APPROVED", "failedFilters", List.of());
		}
		if (path.contains("legal/terms")) {
			return Map.of("version", "terms-educational-v1", "title",
					"Termos de uso educacionais e informativos do Araripe Invest");
		}
		if (path.contains("alerts")) {
			return Map.of("eventType", "PRICE_THRESHOLD_REACHED", "notificationStatus", "PENDING",
					"title", "Limiar superior de preco atingido",
					"source", "araripe-rules", "referenceDate", "2026-07-07",
					"regulatoryNotice", "Alerta informativo factual.");
		}
		if (path.contains("watched-assets")) {
			return Map.of("symbol", "WEGE3", "userLowerPriceThreshold", 33.0, "userUpperPriceThreshold", 48.0,
					"regulatoryNotice", "Ativo acompanhado para alertas informativos definidos pelo usuario.");
		}
		if (path.contains("position-records")) {
			return Map.of("symbol", "WEGE3", "quantity", 10, "userLowerPriceThreshold", 33.0,
					"userUpperPriceThreshold", 48.0,
					"regulatoryNotice", "Cadastro informativo declarado pelo usuario.");
		}
		if (path.contains("context-analyses")) {
			return Map.of("analysisId", "uuid", "validationStatus", "VALID", "processingStatus", "COMPLETED",
					"sources", List.of("OpenAI Web Search"), "tokenUsage",
					Map.of("inputTokens", 1200, "outputTokens", 450, "totalTokens", 1650, "reasoningTokens", 300));
		}
		if (path.contains("screener") || path.contains("asset-studies")) {
			return Map.of("status", "CRITERIOS_ATENDIDOS", "criteriaAdherenceScore", 82,
					"scoreLabel", "Aderencia a criterios do estudo", "studyPriceReference", 42.0,
					"methodology", "Pontuacao deterministica de aderencia aos criterios do estudo.",
					"sources", List.of("brapi", "araripe-indicators", "araripe-rules"),
					"limitations", List.of("Conteudo educacional e informativo."), "failedFilters", List.of());
		}
		return Map.of("status", "OK");
	}
}
