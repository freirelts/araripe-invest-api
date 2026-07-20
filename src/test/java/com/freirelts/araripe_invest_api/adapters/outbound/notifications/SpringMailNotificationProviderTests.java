package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigest;
import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigestItem;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.alerts.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpringMailNotificationProviderTests {

	@Test
	void emailBodySnapshotContainsOnlyInformationalAlertLanguage() {
		SpringMailNotificationProvider provider = new SpringMailNotificationProvider(null,
				new EmailNotificationProperties("no-reply@araripe.test", null));
		DailyNotificationDigest digest = new DailyNotificationDigest(UUID.fromString("11111111-1111-1111-1111-111111111111"),
				"Cliente", "cliente@araripe.test", LocalDate.of(2026, 7, 7), NotificationChannel.EMAIL,
				"informational-events-v1",
				"Araripe Invest - alertas informativos dos ativos acompanhados em 07/07/2026",
				List.of(new DailyNotificationDigestItem(UUID.fromString("22222222-2222-2222-2222-222222222222"),
						UUID.fromString("33333333-3333-3333-3333-333333333333"), null, "WEGE3",
						LocalDate.of(2026, 7, 7), InformationalEventType.PRICE_THRESHOLD_REACHED, Severity.MEDIUM,
						"Limiar superior de preco atingido", "araripe-rules", "informational-events-v1",
						"O fechamento mais recente cruzou o limiar superior cadastrado pelo usuario para acompanhamento informativo.",
						Map.of("matchedThreshold", "USER_UPPER_PRICE_THRESHOLD", "currentPrice", "49.00",
								"priceReferenceDate", "2026-07-07", "userUpperPriceThreshold", "48.00"))));

		String body = provider.body(digest, "araripe-email-test");

		assertThat(body).isEqualTo("""
				Ola, Cliente.

				Resumo diario consolidado de alertas informativos dos ativos acompanhados em 07/07/2026.

				- WEGE3 - Limiar superior de preco atingido
				  Tipo: Limiar de preco definido pelo usuario | Severidade: media
				  O que aconteceu: O fechamento mais recente cruzou o limiar superior cadastrado pelo usuario para acompanhamento informativo.
				  Motivo do alerta: Fechamento de R$ 49.00 em 07/07/2026 ficou igual ou acima do limiar superior cadastrado de R$ 48.00.
				  Auditoria: Fonte araripe-rules | Data de referencia 07/07/2026 | Regra informational-events-v1

				Este alerta e informativo e nao recomenda compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao. Use apenas como apoio educacional.

				Identificador interno: araripe-email-test
				""");
		assertThat(body).doesNotContain("recomendacoes acionaveis", "REALIZAR_OBJETIVO", "EXECUTAR_STOP");
	}

	@Test
	void assumptionChangedEmailExplainsDeterministicReason() {
		SpringMailNotificationProvider provider = new SpringMailNotificationProvider(null,
				new EmailNotificationProperties("no-reply@araripe.test", null));
		DailyNotificationDigest digest = new DailyNotificationDigest(UUID.fromString("11111111-1111-1111-1111-111111111111"),
				"Cliente", "cliente@araripe.test", LocalDate.of(2026, 7, 7), NotificationChannel.EMAIL,
				"informational-events-v1",
				"Araripe Invest - alertas informativos dos ativos acompanhados em 07/07/2026",
				List.of(new DailyNotificationDigestItem(UUID.fromString("22222222-2222-2222-2222-222222222222"),
						UUID.fromString("33333333-3333-3333-3333-333333333333"), null, "TAEE11",
						LocalDate.of(2026, 7, 7), InformationalEventType.STUDY_ASSUMPTION_CHANGED, Severity.HIGH,
						"Premissas do modelo de estudo alteradas", "araripe-rules", "informational-events-v1",
						"Indicadores ou criterios do modelo acompanhado mudaram em relacao ao registro aceito pelo usuario.",
							Map.of("currentStudyStatus", "CRITERIOS_EM_ATENCAO", "acceptedScore", 82, "currentScore", 58,
									"scoreDelta", -24, "currentFailedFilters",
									List.of(Map.of("message", "Lucro caiu abaixo do limite deterministico."))))));

		String body = provider.body(digest, "araripe-email-test");

		assertThat(body)
					.contains("Tipo: Premissa do estudo alterada | Severidade: alta")
					.contains("Status atual do estudo: criterios em atencao.")
				.contains("Score aceito: 82; score atual: 58; variacao: -24 pontos.")
				.contains("O score atual ficou abaixo do minimo informativo de 60 pontos.")
				.contains("Motivos deterministicos: Lucro caiu abaixo do limite deterministico.");
		assertThat(body).doesNotContain("STUDY_ASSUMPTION_CHANGED", "HIGH");
	}

	@Test
	void htmlBodyRendersStyledInformationalDigestAndEscapesDynamicText() {
		SpringMailNotificationProvider provider = new SpringMailNotificationProvider(null,
				new EmailNotificationProperties("no-reply@araripe.test", null));
		DailyNotificationDigest digest = new DailyNotificationDigest(UUID.fromString("11111111-1111-1111-1111-111111111111"),
				"Cliente <Teste>", "cliente@araripe.test", LocalDate.of(2026, 7, 7), NotificationChannel.EMAIL,
				"informational-events-v1",
				"Araripe Invest - alertas informativos dos ativos acompanhados em 07/07/2026",
				List.of(new DailyNotificationDigestItem(UUID.fromString("22222222-2222-2222-2222-222222222222"),
						UUID.fromString("33333333-3333-3333-3333-333333333333"), null, "TAEE11",
						LocalDate.of(2026, 7, 7), InformationalEventType.STUDY_ASSUMPTION_CHANGED, Severity.HIGH,
						"Premissas do modelo de estudo alteradas", "araripe-rules", "informational-events-v1",
						"Indicadores ou criterios do modelo acompanhado mudaram em relacao ao registro aceito pelo usuario.",
								Map.of("currentStudyStatus", "CRITERIOS_EM_ATENCAO", "acceptedScore", 82, "currentScore", 58,
										"scoreDelta", -24))));

		String html = provider.htmlBody(digest, "araripe-email-test");

		assertThat(html)
				.contains("<!doctype html>")
				.contains("background:#172033")
				.contains("Alertas informativos diarios")
				.contains("Cliente &lt;Teste&gt;")
				.contains("TAEE11")
				.contains("Motivo do alerta")
					.contains("Status atual do estudo: criterios em atencao.")
				.contains("Identificador interno: araripe-email-test");
		assertThat(html).doesNotContain("Cliente <Teste>", "STUDY_ASSUMPTION_CHANGED", "HIGH");
	}
}
