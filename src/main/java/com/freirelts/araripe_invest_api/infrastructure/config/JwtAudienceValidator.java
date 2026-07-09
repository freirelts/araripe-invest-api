package com.freirelts.araripe_invest_api.infrastructure.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

	private final String audience;

	JwtAudienceValidator(String audience) {
		this.audience = audience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (token.getAudience().contains(audience)) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token",
				"JWT audience is not accepted by Araripe Invest.", null));
	}
}
