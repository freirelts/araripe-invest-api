package com.freirelts.araripe_invest_api.infrastructure.config;

import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
				.filter(user -> tokenRolesAreStillGranted(token, user))
				.map(user -> OAuth2TokenValidatorResult.success())
				.orElseGet(() -> OAuth2TokenValidatorResult.failure(INVALID_USER));
	}

	private static boolean tokenRolesAreStillGranted(Jwt token, User user) {
		Object rolesClaim = token.getClaims().get("roles");
		if (!(rolesClaim instanceof Collection<?> tokenRoles) || tokenRoles.isEmpty()) {
			return false;
		}

		Set<UserRoleType> currentRoles = user.getRoles().stream()
				.map(userRole -> userRole.getRole())
				.collect(Collectors.toSet());
		for (Object tokenRole : tokenRoles) {
			if (tokenRole == null) {
				return false;
			}
			try {
				if (!currentRoles.contains(UserRoleType.valueOf(tokenRole.toString()))) {
					return false;
				}
			}
			catch (IllegalArgumentException ex) {
				return false;
			}
		}
		return true;
	}
}
