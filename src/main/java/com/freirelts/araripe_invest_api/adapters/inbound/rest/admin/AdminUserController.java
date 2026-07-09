package com.freirelts.araripe_invest_api.adapters.inbound.rest.admin;

import com.freirelts.araripe_invest_api.domain.users.SubscriptionStatus;
import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.domain.users.UserStatus;
import com.freirelts.araripe_invest_api.infrastructure.persistence.UserRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

	private final UserRepository userRepository;

	AdminUserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping
	List<AdminUserResponse> listUsers() {
		return userRepository.findAll().stream()
				.map(AdminUserResponse::from)
				.toList();
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
