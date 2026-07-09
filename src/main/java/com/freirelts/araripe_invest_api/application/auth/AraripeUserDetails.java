package com.freirelts.araripe_invest_api.application.auth;

import com.freirelts.araripe_invest_api.domain.users.User;
import com.freirelts.araripe_invest_api.domain.users.UserRoleType;
import com.freirelts.araripe_invest_api.domain.users.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AraripeUserDetails implements UserDetails {

	private final UUID id;
	private final String email;
	private final String passwordHash;
	private final boolean enabled;
	private final boolean accountNonLocked;
	private final List<GrantedAuthority> authorities;

	public AraripeUserDetails(User user) {
		this.id = user.getId();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.enabled = user.hasValidAuthenticatedAccess();
		this.accountNonLocked = user.getStatus() != UserStatus.BLOCKED;
		this.authorities = user.getRoles().stream()
				.map(role -> new SimpleGrantedAuthority("ROLE_" + role.getRole().name()))
				.map(GrantedAuthority.class::cast)
				.toList();
	}

	public UUID id() {
		return id;
	}

	public List<UserRoleType> roles() {
		return authorities.stream()
				.map(GrantedAuthority::getAuthority)
				.map(authority -> authority.replaceFirst("^ROLE_", ""))
				.map(UserRoleType::valueOf)
				.toList();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return authorities;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonLocked() {
		return accountNonLocked;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}
}
