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
		add(paths, "/api/v1/auth/register", "post", "Cadastro de usuario CUSTOMER.");
		add(paths, "/api/v1/auth/login", "post", "Login com JWT assinado.");
		add(paths, "/api/v1/auth/me", "get", "Usuario autenticado.");
		add(paths, "/api/v1/theses/ranking", "get", "Ranking diario de teses com score, valuation e risco.");
		add(paths, "/api/v1/theses/{thesisId}", "get",
				"Detalhe da tese com filtros, score, fundamentos, alocacao e contexto de IA validado quando existir.");
		add(paths, "/api/v1/theses/history", "get", "Historico de teses por ativo.");
		add(paths, "/api/v1/assets", "get", "Ativos monitorados ativos.");
		add(paths, "/api/v1/assets/{symbol}/fundamentals", "get",
				"Fundamentos, indicadores, demonstrativos e dividendos.");
		add(paths, "/api/v1/assets/{symbol}/diagnostics", "get", "Diagnostico de filtros eliminatorios.");
		add(paths, "/api/v1/allocation-settings", "get", "Parametros pessoais de risco e alocacao.");
		add(paths, "/api/v1/allocation-settings", "put", "Atualiza parametros pessoais de risco e alocacao.");
		add(paths, "/api/v1/portfolio/positions", "get", "Carteira do cliente.");
		add(paths, "/api/v1/portfolio/positions", "post", "Cria posicao do cliente.");
		add(paths, "/api/v1/portfolio/positions/{positionId}", "put", "Edita posicao aberta.");
		add(paths, "/api/v1/portfolio/positions/{positionId}/close", "patch", "Encerra posicao mantendo historico.");
		add(paths, "/api/v1/portfolio/positions/{positionId}/main-thesis", "post", "Associa tese principal.");
		add(paths, "/api/v1/portfolio/positions/{positionId}/main-thesis", "patch", "Troca tese principal.");
		add(paths, "/api/v1/recommendations", "get", "Recomendacoes por posicao do usuario.");
		add(paths, "/api/v1/recommendations/{recommendationId}", "get", "Detalhe de recomendacao por posicao.");
		add(paths, "/api/v1/notifications", "get", "Eventos de notificacao do usuario.");
		add(paths, "/api/v1/notifications/{notificationId}/read", "patch", "Marca notificacao como lida na web.");
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
		if (path.contains("recommendations")) {
			return Map.of("recommendationType", "REAVALIAR", "severity", "HIGH",
					"deterministicReasons", List.of("Posicao exige reavaliacao por regra deterministica."));
		}
		if (path.contains("notifications")) {
			return Map.of("eventType", "REASSESSMENT_REQUIRED", "status", "PENDING", "readAt", "");
		}
		if (path.contains("context-analyses")) {
			return Map.of("analysisId", "uuid", "validationStatus", "VALID", "processingStatus", "COMPLETED",
					"sources", List.of("OpenAI Web Search"));
		}
		if (path.contains("theses")) {
			return Map.of("status", "OPORTUNIDADE", "score", 82, "priceCeiling", 42.0,
					"failedFilters", List.of());
		}
		return Map.of("status", "OK");
	}
}
