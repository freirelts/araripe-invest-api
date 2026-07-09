package com.freirelts.araripe_invest_api.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "araripe.security")
public record AraripeSecurityProperties(
		List<String> allowedOrigins,
		Jwt jwt,
		BootstrapAdmin bootstrapAdmin) {

	public AraripeSecurityProperties {
		if (allowedOrigins == null || allowedOrigins.isEmpty()) {
			allowedOrigins = List.of("http://localhost:4200");
		}
		if (jwt == null) {
			jwt = new Jwt("araripe-invest-api", "araripe-invest-fed",
					"change-this-local-development-secret-with-at-least-32-bytes", Duration.ofHours(2));
		}
		if (bootstrapAdmin == null) {
			bootstrapAdmin = new BootstrapAdmin(false, null, null, "Administrador Araripe");
		}
	}

	public record Jwt(String issuer, String audience, String secret, Duration expiration) {
		public Jwt {
			if (issuer == null || issuer.isBlank()) {
				issuer = "araripe-invest-api";
			}
			if (audience == null || audience.isBlank()) {
				audience = "araripe-invest-fed";
			}
			if (secret == null || secret.isBlank()) {
				secret = "change-this-local-development-secret-with-at-least-32-bytes";
			}
			if (expiration == null || expiration.isNegative() || expiration.isZero()) {
				expiration = Duration.ofHours(2);
			}
		}
	}

	public record BootstrapAdmin(boolean enabled, String email, String password, String name) {
		public BootstrapAdmin {
			if (name == null || name.isBlank()) {
				name = "Administrador Araripe";
			}
		}
	}
}
