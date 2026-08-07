package eu.sonetas.aurevanta.membership;

import java.time.Instant;
import java.util.UUID;

import eu.sonetas.aurevanta.tenant.Tenant;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One person's standing in one organisation.
 *
 * <p>
 * This is what an access token is scoped to: the same {@link User} may hold several
 * memberships with a different {@link UserRole} in each, and a token names exactly one of
 * them.
 */
@Entity
@Table(name = "memberships")
public class Membership {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_accessed_at")
	private Instant lastAccessedAt;

	protected Membership() {
		// for JPA
	}

	public Membership(User user, Tenant tenant, UserRole role, Instant createdAt) {
		this.user = user;
		this.tenant = tenant;
		this.role = role;
		this.createdAt = createdAt;
	}

	/**
	 * Notes that the holder has just chosen this organisation, so a later sign-in can
	 * offer the one they were most recently working in.
	 */
	public void recordAccess(Instant at) {
		this.lastAccessedAt = at;
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public UserRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastAccessedAt() {
		return lastAccessedAt;
	}

}
