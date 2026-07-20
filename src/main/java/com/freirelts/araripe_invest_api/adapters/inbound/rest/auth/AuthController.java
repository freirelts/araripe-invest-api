package com.freirelts.araripe_invest_api.adapters.inbound.rest.auth;

import com.freirelts.araripe_invest_api.application.auth.AuthResult;
import com.freirelts.araripe_invest_api.application.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	AuthResult login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.email(), request.password());
	}

	@GetMapping("/me")
	AuthResult.UserSummary me(JwtAuthenticationToken authentication) {
		return authService.currentUser((Jwt) authentication.getPrincipal());
	}

	record LoginRequest(
			@NotBlank
			@Email
			@Size(max = 320)
			String email,
			@NotBlank
			@Size(max = 120)
			String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=%s, password=<masked>]".formatted(email);
		}
	}
}
