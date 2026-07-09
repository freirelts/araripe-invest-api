package com.freirelts.araripe_invest_api.application.auth;

import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.infrastructure.config.AraripeSecurityProperties;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
class BootstrapAdminInitializer implements ApplicationRunner {

	private final AraripeSecurityProperties properties;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	BootstrapAdminInitializer(AraripeSecurityProperties properties, UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.properties = properties;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		AraripeSecurityProperties.BootstrapAdmin bootstrapAdmin = properties.bootstrapAdmin();
		if (!bootstrapAdmin.enabled()) {
			return;
		}
		if (isBlank(bootstrapAdmin.email()) || isBlank(bootstrapAdmin.password())) {
			throw new IllegalStateException("Bootstrap admin requires email and password when enabled.");
		}
		if (bootstrapAdmin.password().length() < 12) {
			throw new IllegalStateException("Bootstrap admin password must have at least 12 characters.");
		}

		String email = bootstrapAdmin.email().trim().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmailIgnoreCase(email)) {
			return;
		}

		User admin = new User(bootstrapAdmin.name().trim(), email, passwordEncoder.encode(bootstrapAdmin.password()),
				SubscriptionStatus.NONE);
		admin.addRole(UserRoleType.ADMIN);
		userRepository.save(admin);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
