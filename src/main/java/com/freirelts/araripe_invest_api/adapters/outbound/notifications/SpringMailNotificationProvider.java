package com.freirelts.araripe_invest_api.adapters.outbound.notifications;

import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigest;
import com.freirelts.araripe_invest_api.application.notifications.DailyNotificationDigestItem;
import com.freirelts.araripe_invest_api.application.notifications.NotificationProvider;
import com.freirelts.araripe_invest_api.application.notifications.NotificationPublishResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class SpringMailNotificationProvider implements NotificationProvider {

	static final String PROVIDER_NAME = "spring-mail";
	private static final Logger log = LoggerFactory.getLogger(SpringMailNotificationProvider.class);
	private static final DateTimeFormatter EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(properties.from());
			helper.setTo(digest.recipientEmail());
			helper.setSubject(digest.subject());
			helper.setText(body(digest, internalMessageId), htmlBody(digest, internalMessageId));
			if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
				helper.setReplyTo(properties.replyTo());
			}
			mailSender.send(message);
		}
		catch (MessagingException | RuntimeException ex) {
			log.warn("Notification provider failed to send email provider={} messageId={} userId={} recipient={} items={}",
					PROVIDER_NAME, internalMessageId, digest.userId(), digest.recipientEmail(), digest.items().size(), ex);
			if (ex instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			throw new IllegalStateException("Could not build informational alert email.", ex);
		}
		return new NotificationPublishResult(PROVIDER_NAME, internalMessageId);
	}

	String body(DailyNotificationDigest digest, String internalMessageId) {
		StringBuilder body = new StringBuilder();
		body.append("Ola, ").append(digest.recipientName()).append(".\n\n");
		body.append("Resumo diario consolidado de alertas informativos dos ativos acompanhados em ")
				.append(formatDate(digest.referenceDate()))
				.append(".\n\n");
		for (DailyNotificationDigestItem item : digest.items()) {
			body.append("- ")
					.append(item.symbol())
					.append(" - ")
					.append(item.title())
					.append("\n  Tipo: ")
					.append(eventTypeLabel(item))
					.append(" | Severidade: ")
					.append(severityLabel(item))
					.append("\n  O que aconteceu: ")
					.append(item.summary())
					.append("\n  Motivo do alerta: ")
					.append(reason(item))
					.append("\n  Auditoria: Fonte ")
					.append(item.source())
					.append(" | Data de referencia ")
					.append(formatDate(item.referenceDate()))
					.append(" | Regra ")
					.append(item.ruleVersion())
					.append("\n");
		}
		body.append("\n");
		body.append(disclaimer(digest)).append("\n");
		body.append("\nIdentificador interno: ").append(internalMessageId).append("\n");
		return body.toString();
	}

	String htmlBody(DailyNotificationDigest digest, String internalMessageId) {
		StringBuilder html = new StringBuilder();
		html.append("""
				<!doctype html>
				<html lang="pt-BR">
				<head>
				  <meta charset="UTF-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1.0">
				  <title>Araripe Invest</title>
				</head>
				<body style="margin:0;padding:0;background:#f5f7fb;color:#172033;font-family:Arial,Helvetica,sans-serif;">
				  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:#f5f7fb;margin:0;padding:24px 0;">
				    <tr>
				      <td align="center" style="padding:0 12px;">
				        <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:720px;background:#ffffff;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;">
				          <tr>
				            <td style="background:#172033;color:#ffffff;padding:24px 28px;">
				              <div style="font-size:13px;letter-spacing:.08em;text-transform:uppercase;color:#a7c7e7;">Araripe Invest</div>
				              <h1 style="margin:8px 0 0;font-size:24px;line-height:1.25;font-weight:700;">Alertas informativos diarios</h1>
				              <p style="margin:10px 0 0;font-size:14px;line-height:1.5;color:#d8e2ef;">Ativos acompanhados em""")
				.append(" ")
				.append(escape(formatDate(digest.referenceDate())))
				.append("""
				              </p>
				            </td>
				          </tr>
				          <tr>
				            <td style="padding:24px 28px 8px;">
				              <p style="margin:0 0 18px;font-size:15px;line-height:1.55;color:#334155;">Ola,""")
				.append(" ")
				.append(escape(digest.recipientName()))
				.append("""
				              . Segue o resumo consolidado dos eventos factuais identificados pelas regras do Araripe Invest.</p>
				""");
		for (DailyNotificationDigestItem item : digest.items()) {
			appendHtmlItem(html, item);
		}
		html.append("""
				            </td>
				          </tr>
				          <tr>
				            <td style="padding:8px 28px 24px;">
				              <div style="border:1px solid #dbeafe;background:#eff6ff;border-radius:8px;padding:14px 16px;color:#1e3a5f;font-size:13px;line-height:1.5;">
				""")
				.append(escape(disclaimer(digest)))
				.append("""
				              </div>
				              <p style="margin:18px 0 0;color:#64748b;font-size:12px;line-height:1.4;">Identificador interno:""")
				.append(" ")
				.append(escape(internalMessageId))
				.append("""
				              </p>
				            </td>
				          </tr>
				        </table>
				      </td>
				    </tr>
				  </table>
				</body>
				</html>
				""");
		return html.toString();
	}

	private void appendHtmlItem(StringBuilder html, DailyNotificationDigestItem item) {
		html.append("""
				              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="border:1px solid #e2e8f0;border-radius:8px;margin:0 0 14px;overflow:hidden;">
				                <tr>
				                  <td style="padding:16px 18px;background:#ffffff;">
				                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
				                      <tr>
				                        <td style="vertical-align:top;">
				                          <div style="font-size:20px;line-height:1.2;font-weight:700;color:#172033;">""")
				.append(escape(item.symbol()))
				.append("""
				                          </div>
				                          <div style="margin-top:4px;font-size:15px;line-height:1.4;color:#334155;font-weight:600;">""")
				.append(escape(item.title()))
				.append("""
				                          </div>
				                        </td>
				                        <td align="right" style="vertical-align:top;white-space:nowrap;">
				                          <span style="display:inline-block;border-radius:999px;padding:5px 10px;font-size:12px;font-weight:700;""")
				.append(severityStyle(item))
				.append("\">")
				.append(escape(severityLabel(item)))
				.append("""
				</span>
				                        </td>
				                      </tr>
				                    </table>
				                    <div style="margin-top:12px;font-size:13px;color:#475569;">""")
				.append(escape(eventTypeLabel(item)))
				.append("""
				</div>
				                    <div style="margin-top:14px;border-top:1px solid #eef2f7;padding-top:14px;">
				                      <div style="font-size:12px;font-weight:700;color:#64748b;text-transform:uppercase;">O que aconteceu</div>
				                      <p style="margin:5px 0 0;font-size:14px;line-height:1.55;color:#263449;">""")
				.append(escape(item.summary()))
				.append("""
				</p>
				                    </div>
				                    <div style="margin-top:14px;background:#f8fafc;border-left:4px solid #2f6f88;border-radius:6px;padding:12px 14px;">
				                      <div style="font-size:12px;font-weight:700;color:#475569;text-transform:uppercase;">Motivo do alerta</div>
				                      <p style="margin:5px 0 0;font-size:14px;line-height:1.55;color:#172033;">""")
				.append(escape(reason(item)))
				.append("""
				</p>
				                    </div>
				                    <div style="margin-top:14px;font-size:12px;line-height:1.45;color:#64748b;">Fonte:""")
				.append(" ")
				.append(escape(item.source()))
				.append(" | Data de referencia: ")
				.append(escape(formatDate(item.referenceDate())))
				.append(" | Regra: ")
				.append(escape(item.ruleVersion()))
				.append("""
				</div>
				                  </td>
				                </tr>
				              </table>
				""");
	}

	private String disclaimer(DailyNotificationDigest digest) {
		String subject = digest.items().size() == 1 ? "Este alerta e informativo" : "Estes alertas sao informativos";
		return subject
				+ " e nao recomenda compra, venda, manutencao, aumento, reducao, alocacao ou encerramento de posicao. Use apenas como apoio educacional.";
	}

	private String severityStyle(DailyNotificationDigestItem item) {
		return switch (item.severity()) {
			case LOW -> "background:#e8f5e9;color:#1f6f43;border:1px solid #b8e0c3;";
			case MEDIUM -> "background:#fff7ed;color:#9a4d00;border:1px solid #fed7aa;";
			case HIGH -> "background:#fef2f2;color:#b42318;border:1px solid #fecaca;";
			case CRITICAL -> "background:#450a0a;color:#ffffff;border:1px solid #7f1d1d;";
		};
	}

	private String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;")
				.replace("'", "&#39;");
	}

	private String eventTypeLabel(DailyNotificationDigestItem item) {
		return switch (item.eventType()) {
			case PRICE_THRESHOLD_REACHED -> "Limiar de preco definido pelo usuario";
			case DATA_UPDATED -> "Dado atualizado";
			case DATA_STALE -> "Dado ou estudo desatualizado";
			case INDICATOR_THRESHOLD_REACHED -> "Indicador ou referencia do estudo atingida";
			case STUDY_ASSUMPTION_CHANGED -> "Premissa do estudo alterada";
			case QUALITY_DATA_BLOCKED -> "Bloqueio por qualidade de dados";
		};
	}

	private String severityLabel(DailyNotificationDigestItem item) {
		return switch (item.severity()) {
			case LOW -> "baixa";
			case MEDIUM -> "media";
			case HIGH -> "alta";
			case CRITICAL -> "critica";
		};
	}

	private String reason(DailyNotificationDigestItem item) {
		return switch (item.eventType()) {
			case PRICE_THRESHOLD_REACHED -> priceThresholdReason(item);
			case INDICATOR_THRESHOLD_REACHED -> indicatorThresholdReason(item);
			case STUDY_ASSUMPTION_CHANGED -> studyAssumptionChangedReason(item);
			case DATA_STALE -> staleDataReason(item);
			case QUALITY_DATA_BLOCKED -> qualityBlockedReason(item);
			case DATA_UPDATED -> "Uma fonte ou demonstrativo relevante foi atualizado pelas regras deterministicas.";
		};
	}

	private String priceThresholdReason(DailyNotificationDigestItem item) {
		Map<String, Object> evidence = evidence(item);
		BigDecimal currentPrice = decimal(evidence.get("currentPrice"));
		String priceDate = date(evidence.get("priceReferenceDate"));
		String matchedThreshold = text(evidence.get("matchedThreshold"));
		if ("USER_LOWER_PRICE_THRESHOLD".equals(matchedThreshold)) {
			BigDecimal threshold = decimal(evidence.get("userLowerPriceThreshold"));
			if (currentPrice != null && threshold != null) {
				return "Fechamento de " + money(currentPrice) + " em " + priceDate
						+ " ficou igual ou abaixo do limiar inferior cadastrado de " + money(threshold) + ".";
			}
		}
		if ("USER_UPPER_PRICE_THRESHOLD".equals(matchedThreshold)) {
			BigDecimal threshold = decimal(evidence.get("userUpperPriceThreshold"));
			if (currentPrice != null && threshold != null) {
				return "Fechamento de " + money(currentPrice) + " em " + priceDate
						+ " ficou igual ou acima do limiar superior cadastrado de " + money(threshold) + ".";
			}
		}
		return "O fechamento mais recente cruzou um limiar de preco cadastrado para acompanhamento informativo.";
	}

	private String indicatorThresholdReason(DailyNotificationDigestItem item) {
		Map<String, Object> evidence = evidence(item);
		BigDecimal currentPrice = decimal(evidence.get("currentPrice"));
		BigDecimal studyReference = decimal(evidence.get("studyPriceReference"));
		String priceDate = date(evidence.get("priceReferenceDate"));
		if (currentPrice != null && studyReference != null) {
			String difference = percentageDifference(currentPrice, studyReference);
			return "Fechamento de " + money(currentPrice) + " em " + priceDate
					+ " ficou acima da referencia do estudo de " + money(studyReference) + difference + ".";
		}
		return "Um indicador ou referencia analitica cruzou o limite definido pelas regras do estudo.";
	}

	private String studyAssumptionChangedReason(DailyNotificationDigestItem item) {
		Map<String, Object> evidence = evidence(item);
		StringBuilder reason = new StringBuilder();
		String currentStatus = thesisStatusLabel(text(evidence.get("currentStudyStatus")));
		Integer acceptedScore = integer(evidence.get("acceptedScore"));
		Integer currentScore = integer(evidence.get("currentScore"));
		Integer scoreDelta = integer(evidence.get("scoreDelta"));
		if (currentStatus != null) {
			reason.append("Status atual do estudo: ").append(currentStatus).append(".");
		}
		if (acceptedScore != null && currentScore != null) {
			appendSentenceSeparator(reason);
			reason.append("Score aceito: ").append(acceptedScore).append("; score atual: ").append(currentScore);
			if (scoreDelta != null) {
				reason.append("; variacao: ").append(signed(scoreDelta)).append(" pontos");
			}
			reason.append(".");
		}
		appendIfPresent(reason, thresholdCause(acceptedScore, currentScore, scoreDelta));
		String failedFilters = conciseReasons(evidence.get("currentFailedFilters"), "message", 3);
		if (failedFilters != null) {
			appendSentenceSeparator(reason);
			reason.append("Motivos deterministicos: ").append(failedFilters).append(".");
		}
		if (reason.length() == 0) {
			return "O modelo atual apresentou mudanca rastreavel de criterios ou indicadores em relacao ao registro aceito.";
		}
		return reason.toString();
	}

	private String staleDataReason(DailyNotificationDigestItem item) {
		String studyDate = date(evidence(item).get("studyReferenceDate"));
		if (studyDate != null) {
			return "O estudo mais recente disponivel para comparacao tem data de referencia " + studyDate
					+ ", fora da tolerancia operacional da varredura.";
		}
		return "Nao havia estudo recente suficiente para comparar as premissas do ativo acompanhado.";
	}

	private String qualityBlockedReason(DailyNotificationDigestItem item) {
		String priceQualityStatus = text(evidence(item).get("priceQualityStatus"));
		if (priceQualityStatus != null) {
			return "O dado de preco usado na varredura apresentou qualidade " + priceQualityStatus
					+ ", insuficiente para publicar comparacao confiavel.";
		}
		return "Dados essenciais estavam ausentes, inconsistentes ou sem qualidade valida para a varredura.";
	}

	private String thresholdCause(Integer acceptedScore, Integer currentScore, Integer scoreDelta) {
		if (currentScore != null && currentScore < 60) {
			return "O score atual ficou abaixo do minimo informativo de 60 pontos.";
		}
		if (scoreDelta != null && scoreDelta <= -20) {
			return "A queda de score atingiu " + Math.abs(scoreDelta) + " pontos em relacao ao registro aceito.";
		}
		if (acceptedScore != null && currentScore != null && acceptedScore - currentScore >= 20) {
			return "A queda de score atingiu " + (acceptedScore - currentScore)
					+ " pontos em relacao ao registro aceito.";
		}
		return null;
	}

	private void appendIfPresent(StringBuilder text, String sentence) {
		if (sentence == null || sentence.isBlank()) {
			return;
		}
		appendSentenceSeparator(text);
		text.append(sentence);
	}

	private void appendSentenceSeparator(StringBuilder text) {
		if (text.length() > 0) {
			text.append(" ");
		}
	}

	private String thesisStatusLabel(String status) {
		if (status == null) {
			return null;
		}
		return switch (status) {
			case "DADOS_INSUFICIENTES" -> "dados insuficientes";
			case "EM_ESTUDO" -> "em estudo";
			case "CRITERIOS_ATENDIDOS" -> "criterios atendidos";
			case "CRITERIOS_PARCIALMENTE_ATENDIDOS" -> "criterios parcialmente atendidos";
			case "CRITERIOS_EM_ATENCAO" -> "criterios em atencao";
			case "PREMISSAS_ALTERADAS" -> "premissas alteradas";
			case "DADOS_DESATUALIZADOS" -> "dados desatualizados";
			default -> status;
		};
	}

	private String conciseReasons(Object value, String field, int limit) {
		if (!(value instanceof List<?> list) || list.isEmpty()) {
			return null;
		}
		return list.stream()
				.limit(limit)
				.map(item -> fieldValue(item, field))
				.filter(text -> text != null && !text.isBlank())
				.reduce((left, right) -> left + "; " + right)
				.orElse(null);
	}

	@SuppressWarnings("unchecked")
	private String fieldValue(Object item, String field) {
		if (item instanceof Map<?, ?> map) {
			return text(((Map<String, Object>) map).get(field));
		}
		return text(item);
	}

	private String percentageDifference(BigDecimal currentPrice, BigDecimal reference) {
		if (reference == null || reference.signum() <= 0) {
			return "";
		}
		BigDecimal delta = currentPrice.subtract(reference)
				.multiply(new BigDecimal("100"))
				.divide(reference, 1, RoundingMode.HALF_UP);
		return " (" + signed(delta) + "%)";
	}

	private String money(BigDecimal value) {
		return "R$ " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}

	private String signed(Integer value) {
		return value > 0 ? "+" + value : value.toString();
	}

	private String signed(BigDecimal value) {
		return value.signum() > 0 ? "+" + value.toPlainString() : value.toPlainString();
	}

	private Map<String, Object> evidence(DailyNotificationDigestItem item) {
		return item.evidence() == null ? Map.of() : item.evidence();
	}

	private BigDecimal decimal(Object value) {
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value instanceof Number number) {
			return BigDecimal.valueOf(number.doubleValue());
		}
		if (value instanceof String text && !text.isBlank()) {
			try {
				return new BigDecimal(text);
			}
			catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private Integer integer(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		if (value instanceof String text && !text.isBlank()) {
			try {
				return Integer.parseInt(text);
			}
			catch (NumberFormatException ignored) {
				return null;
			}
		}
		return null;
	}

	private String date(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDate date) {
			return formatDate(date);
		}
		if (value instanceof List<?> values && values.size() >= 3) {
			Integer year = integer(values.get(0));
			Integer month = integer(values.get(1));
			Integer day = integer(values.get(2));
			if (year != null && month != null && day != null) {
				return formatDate(LocalDate.of(year, month, day));
			}
		}
		if (value instanceof String text && !text.isBlank()) {
			try {
				return formatDate(LocalDate.parse(text));
			}
			catch (RuntimeException ignored) {
				return text;
			}
		}
		return text(value);
	}

	private String formatDate(LocalDate date) {
		return date == null ? null : date.format(EMAIL_DATE_FORMAT);
	}

	private String text(Object value) {
		if (value == null) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.isBlank() ? null : text;
	}
}
