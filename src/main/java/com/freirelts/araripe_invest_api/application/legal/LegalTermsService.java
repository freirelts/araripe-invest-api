package com.freirelts.araripe_invest_api.application.legal;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class LegalTermsService {

	public static final String CURRENT_VERSION = "terms-educational-v1";
	private static final LocalDate EFFECTIVE_DATE = LocalDate.of(2026, 7, 16);

	public LegalTerms currentTerms() {
		return new LegalTerms(CURRENT_VERSION, EFFECTIVE_DATE,
				"Termos de uso educacionais e informativos do Araripe Invest",
				List.of(
						"O Araripe Invest oferece conteudo educacional, informativo e analitico para estudo e acompanhamento de ativos brasileiros.",
						"O produto nao presta consultoria de valores mobiliarios, analise credenciada, administracao de carteira, suitability, intermedicacao ou execucao de ordens.",
						"Nao ha recomendacao individualizada, promessa de retorno, promessa de seguranca, decisao automatizada de investimento ou comando de compra, venda, manutencao, aumento, reducao, alocacao ou encerramento.",
						"Alertas informativos representam eventos factuais, como limiar de preco atingido, dado atualizado, dado desatualizado, indicador fora de intervalo, premissa alterada ou bloqueio de qualidade.",
						"Mercado financeiro envolve risco; resultados passados nao garantem resultados futuros; dados incompletos, desatualizados ou inconsistentes bloqueiam modelos de estudo e alertas."),
				"Submeter estes textos a revisao juridica antes de uso comercial amplo.");
	}

	public boolean isCurrentVersion(String version) {
		return CURRENT_VERSION.equals(version);
	}

	public record LegalTerms(
			String version,
			LocalDate effectiveDate,
			String title,
			List<String> clauses,
			String legalReviewNotice) {
	}
}
