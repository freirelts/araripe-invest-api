package com.freirelts.araripe_invest_api.adapters.inbound.rest.admin;

import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.domain.users.UserStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	AdminUserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping
	List<AdminUserResponse> listUsers() {
		return userRepository.findAll().stream()
				.map(AdminUserResponse::from)
				.toList();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AdminUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(CONFLICT, "E-mail already registered.");
		}
		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
				request.subscriptionStatus());
		user.setStatus(request.status());
		user.replaceRoles(toRoleSet(request.roles()));
		return AdminUserResponse.from(userRepository.saveAndFlush(user));
	}

	@PutMapping("/{userId}")
	AdminUserResponse updateUser(@PathVariable UUID userId, @Valid @RequestBody UpdateUserRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found."));
		String email = normalizeEmail(request.email());
		userRepository.findByEmailIgnoreCase(email)
				.filter(existing -> !existing.getId().equals(userId))
				.ifPresent(existing -> {
					throw new ResponseStatusException(CONFLICT, "E-mail already registered.");
				});

		user.setName(request.name().trim());
		user.setEmail(email);
		user.setStatus(request.status());
		user.setSubscriptionStatus(request.subscriptionStatus());
		user.replaceRoles(toRoleSet(request.roles()));
		user.setUpdatedAt(Instant.now());
		return AdminUserResponse.from(userRepository.saveAndFlush(user));
	}

	private static Set<UserRoleType> toRoleSet(List<UserRoleType> roles) {
		EnumSet<UserRoleType> updatedRoles = EnumSet.noneOf(UserRoleType.class);
		updatedRoles.addAll(roles);
		return updatedRoles;
	}

	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	record UpdateUserRequest(
			@NotBlank
			@Size(max = 160)
			String name,
			@NotBlank
			@Email
			@Size(max = 320)
			String email,
			@NotNull
			UserStatus status,
			@NotNull
			SubscriptionStatus subscriptionStatus,
			@NotEmpty
			List<@NotNull UserRoleType> roles) {
	}

	record CreateUserRequest(
			@NotBlank
			@Size(max = 160)
			String name,
			@NotBlank
			@Email
			@Size(max = 320)
			String email,
			@NotBlank
			@Size(min = 8, max = 120)
			String password,
			@NotNull
			UserStatus status,
			@NotNull
			SubscriptionStatus subscriptionStatus,
			@NotEmpty
			List<@NotNull UserRoleType> roles) {

		@Override
		public String toString() {
			return "CreateUserRequest[name=%s, email=%s, password=<masked>]".formatted(name, email);
		}
	}

	record AdminUserResponse(
			UUID id,
			String name,
			String email,
			UserStatus status,
			SubscriptionStatus subscriptionStatus,
			List<UserRoleType> roles) {

		static AdminUserResponse from(User user) {
			return new AdminUserResponse(user.getId(), user.getName(), user.getEmail(), user.getStatus(),
					user.getSubscriptionStatus(), user.getRoles().stream().map(role -> role.getRole()).toList());
		}
	}
}
