package com.freirelts.araripe_invest_api.domain.users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "app_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserStatus status = UserStatus.ACTIVE;

	@Enumerated(EnumType.STRING)
	@Column(name = "subscription_status", nullable = false, length = 32)
	private SubscriptionStatus subscriptionStatus = SubscriptionStatus.NONE;

	@Column(name = "subscription_plan", length = 80)
	private String subscriptionPlan;

	@Column(name = "subscription_started_at")
	private Instant subscriptionStartedAt;

	@Column(name = "subscription_expires_at")
	private Instant subscriptionExpiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "terms_version_accepted", length = 40)
	private String termsVersionAccepted;

	@Column(name = "terms_accepted_at")
	private Instant termsAcceptedAt;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<UserRole> roles = new LinkedHashSet<>();

	public User(String name, String email, String passwordHash, SubscriptionStatus subscriptionStatus) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.subscriptionStatus = subscriptionStatus;
	}

	public void addRole(UserRoleType role) {
		if (hasRole(role)) {
			return;
		}
		roles.add(new UserRole(this, role));
	}

	public boolean hasRole(UserRoleType role) {
		return roles.stream().anyMatch(userRole -> userRole.getRole() == role);
	}

	public void replaceRoles(Set<UserRoleType> updatedRoles) {
		roles.removeIf(userRole -> !updatedRoles.contains(userRole.getRole()));
		updatedRoles.forEach(this::addRole);
	}

	public boolean hasValidAuthenticatedAccess() {
		if (status != UserStatus.ACTIVE) {
			return false;
		}
		if (hasRole(UserRoleType.ADMIN)) {
			return true;
		}
		return hasRole(UserRoleType.CUSTOMER)
				&& (subscriptionStatus == SubscriptionStatus.ACTIVE || subscriptionStatus == SubscriptionStatus.TRIALING);
	}

	public void acceptTerms(String version, Instant acceptedAt) {
		this.termsVersionAccepted = version;
		this.termsAcceptedAt = acceptedAt;
		this.updatedAt = acceptedAt;
	}
}
