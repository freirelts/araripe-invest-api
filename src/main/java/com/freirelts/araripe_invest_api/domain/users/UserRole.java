package com.freirelts.araripe_invest_api.domain.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user_roles", uniqueConstraints = @UniqueConstraint(name = "uk_user_roles_user_role", columnNames = {
		"user_id", "role" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRole {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserRoleType role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	UserRole(User user, UserRoleType role) {
		this.user = user;
		this.role = role;
	}
}
