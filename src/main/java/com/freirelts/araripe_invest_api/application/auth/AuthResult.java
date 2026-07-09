package com.freirelts.araripe_invest_api.application.auth;

import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.domain.users.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthResult(
		String accessToken,
		String tokenType,
		Instant expiresAt,
		UserSummary user) {

	@Override
	public String toString() {
		return "AuthResult[accessToken=<masked>, tokenType=%s, expiresAt=%s, user=%s]"
				.formatted(tokenType, expiresAt, user);
	}

	public record UserSummary(
			UUID id,
			String name,
			String email,
			UserStatus status,
			SubscriptionStatus subscriptionStatus,
			List<UserRoleType> roles) {
	}
}
