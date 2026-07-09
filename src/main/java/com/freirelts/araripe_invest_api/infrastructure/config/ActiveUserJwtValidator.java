package com.freirelts.araripe_invest_api.infrastructure.config;

import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ActiveUserJwtValidator implements OAuth2TokenValidator<Jwt> {

	private static final OAuth2Error INVALID_USER = new OAuth2Error("invalid_token",
			"JWT subject is not allowed to access Araripe Invest.", null);

	private final UserRepository userRepository;

	ActiveUserJwtValidator(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		UUID userId;
		try {
			userId = UUID.fromString(token.getSubject());
		}
		catch (RuntimeException ex) {
			return OAuth2TokenValidatorResult.failure(INVALID_USER);
		}

		return userRepository.findById(userId)
				.filter(user -> user.hasValidAuthenticatedAccess() && !user.getRoles().isEmpty())
				.map(user -> OAuth2TokenValidatorResult.success())
				.orElseGet(() -> OAuth2TokenValidatorResult.failure(INVALID_USER));
	}
}
