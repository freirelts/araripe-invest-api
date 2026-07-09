package com.freirelts.araripe_invest_api.application.auth;

import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.config.AraripeSecurityProperties;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthenticationManager authenticationManager;
	private final JwtTokenService jwtTokenService;
	private final AraripeSecurityProperties properties;

	AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuthenticationManager authenticationManager, JwtTokenService jwtTokenService,
			AraripeSecurityProperties properties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.authenticationManager = authenticationManager;
		this.jwtTokenService = jwtTokenService;
		this.properties = properties;
	}

	@Transactional
	public AuthResult registerCustomer(String name, String email, String rawPassword) {
		String normalizedEmail = normalizeEmail(email);
		if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "E-mail already registered.");
		}

		User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(rawPassword),
				SubscriptionStatus.TRIALING);
		user.addRole(UserRoleType.CUSTOMER);
		User persisted = userRepository.saveAndFlush(user);
		return issueToken(new AraripeUserDetails(persisted), persisted);
	}

	@Transactional
	public AuthResult login(String email, String rawPassword) {
		try {
			var authentication = authenticationManager.authenticate(
					new UsernamePasswordAuthenticationToken(normalizeEmail(email), rawPassword));
			AraripeUserDetails userDetails = (AraripeUserDetails) authentication.getPrincipal();
			User user = userRepository.findById(userDetails.id())
					.orElseThrow(() -> unauthorized());
			if (!user.hasValidAuthenticatedAccess()) {
				throw unauthorized();
			}
			user.setLastLoginAt(Instant.now());
			return issueToken(userDetails, user);
		}
		catch (AuthenticationException ex) {
			throw unauthorized();
		}
	}

	@Transactional(readOnly = true)
	public AuthResult.UserSummary currentUser(Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		User user = userRepository.findById(userId)
				.filter(User::hasValidAuthenticatedAccess)
				.orElseThrow(this::unauthorized);
		return summary(user);
	}

	private AuthResult issueToken(AraripeUserDetails userDetails, User user) {
		String token = jwtTokenService.issueAccessToken(userDetails);
		Instant expiresAt = Instant.now().plus(properties.jwt().expiration());
		return new AuthResult(token, "Bearer", expiresAt, summary(user));
	}

	private AuthResult.UserSummary summary(User user) {
		return new AuthResult.UserSummary(user.getId(), user.getName(), user.getEmail(), user.getStatus(),
				user.getSubscriptionStatus(), user.getRoles().stream().map(role -> role.getRole()).toList());
	}

	private ResponseStatusException unauthorized() {
		return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
