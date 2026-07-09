package com.freirelts.araripe_invest_api.adapters.inbound.rest.auth;

import com.freirelts.araripe_invest_api.application.auth.AuthResult;
import com.freirelts.araripe_invest_api.application.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final AuthService authService;

	AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResult register(@Valid @RequestBody RegisterRequest request) {
		return authService.registerCustomer(request.name(), request.email(), request.password());
	}

	@PostMapping("/login")
	AuthResult login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request.email(), request.password());
	}

	@GetMapping("/me")
	AuthResult.UserSummary me(JwtAuthenticationToken authentication) {
		return authService.currentUser((Jwt) authentication.getPrincipal());
	}

	record RegisterRequest(
			@NotBlank
			@Size(max = 160)
			String name,
			@NotBlank
			@Email
			@Size(max = 320)
			String email,
			@NotBlank
			@Size(min = 8, max = 120)
			String password) {

		@Override
		public String toString() {
			return "RegisterRequest[name=%s, email=%s, password=<masked>]".formatted(name, email);
		}
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
