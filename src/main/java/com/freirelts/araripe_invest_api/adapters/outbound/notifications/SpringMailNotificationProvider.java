package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigest;
import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigestItem;
import com.freirelts.araripe_invest_api.application.notifications.NotificationProvider;
import com.freirelts.araripe_invest_api.application.notifications.NotificationPublishResult;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

class SpringMailNotificationProvider implements NotificationProvider {

	static final String PROVIDER_NAME = "spring-mail";

	private final JavaMailSender mailSender;
	private final EmailNotificationProperties properties;

	SpringMailNotificationProvider(JavaMailSender mailSender, EmailNotificationProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public String providerName() {
		return PROVIDER_NAME;
	}

	@Override
	public NotificationPublishResult publish(DailyNotificationDigest digest) {
		String internalMessageId = "araripe-email-" + UUID.randomUUID();
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(digest.recipientEmail());
		message.setSubject(digest.subject());
		message.setText(body(digest, internalMessageId));
		if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
			message.setReplyTo(properties.replyTo());
		}
		mailSender.send(message);
		return new NotificationPublishResult(PROVIDER_NAME, internalMessageId);
	}

	private String body(DailyNotificationDigest digest, String internalMessageId) {
		StringBuilder body = new StringBuilder();
		body.append("Ola, ").append(digest.recipientName()).append(".\n\n");
		body.append("Resumo diario consolidado de recomendacoes acionaveis da sua carteira em ")
				.append(digest.referenceDate())
				.append(".\n\n");
		for (DailyNotificationDigestItem item : digest.items()) {
			body.append("- ")
					.append(item.symbol())
					.append(" | ")
					.append(item.recommendationType())
					.append(" | ")
					.append(item.eventType())
					.append(" | Severidade: ")
					.append(item.severity())
					.append("\n  ")
					.append(item.summary())
					.append("\n");
		}
		body.append("\nEste alerta nao executa compra ou venda automaticamente. A decisao deve seguir seu plano de ")
				.append("position trade e os pontos de reavaliacao cadastrados.\n");
		body.append("\nIdentificador interno: ").append(internalMessageId).append("\n");
		return body.toString();
	}
}
