package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigest;
import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigestItem;
import com.freirelts.araripe_invest_api.domain.alerts.InformationalEventType;
import com.freirelts.araripe_invest_api.domain.notifications.NotificationChannel;
import com.freirelts.araripe_invest_api.domain.alerts.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
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
						"O fechamento mais recente cruzou o limiar superior cadastrado pelo usuario para acompanhamento informativo.")));

		String body = provider.body(digest, "araripe-email-test");

		assertThat(body).isEqualTo("""
				Ola, Cliente.

				Resumo diario consolidado de alertas informativos dos ativos acompanhados em 2026-07-07.

				- WEGE3 | PRICE_THRESHOLD_REACHED | Severidade: MEDIUM
				  Limiar superior de preco atingido
				  O fechamento mais recente cruzou o limiar superior cadastrado pelo usuario para acompanhamento informativo.
				  Fonte: araripe-rules | Data de referencia: 2026-07-07 | Regra: informational-events-v1

				Este alerta e informativo e nao recomenda compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao. Use-o apenas como apoio educacional.

				Identificador interno: araripe-email-test
				""");
		assertThat(body).doesNotContain("recomendacoes acionaveis", "REALIZAR_OBJETIVO", "EXECUTAR_STOP");
	}
}
