package eu.sonetas.aurevanta.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A person, identified globally by their email address.
 *
 * <p>
 * Deliberately carries no tenant and no role: what a person may do belongs to a
 * {@code Membership}, one per organisation they belong to. Credentials live here so that
 * someone in several organisations still signs in once, with one password.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 200)
	private String displayName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Null until the address is proved; an account with none cannot sign in. */
	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	protected User() {
		// for JPA
	}

	public User(String email, String passwordHash, String displayName, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isEmailVerified() {
		return this.emailVerifiedAt != null;
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	/**
	 * Replaces the credential.
	 *
	 * <p>
	 * Takes a hash, never a password: this class has no encoder and should not acquire
	 * one, so there is no way to reach it that stores a password in the clear by
	 * accident.
	 *
	 * <p>
	 * Access tokens already issued survive this. They are stateless and signed, so
	 * nothing can withdraw one before it expires — changing a password ends no session
	 * that is already running.
	 */
	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	/**
	 * Records that the address has been proved, keeping the first time it happened.
	 * Verification can be reached by more than one route — a confirmation link, and from
	 * Step 6 a password reset — so arriving twice is expected and must not rewrite
	 * history.
	 */
	public void markEmailVerified(Instant at) {
		if (this.emailVerifiedAt == null) {
			this.emailVerifiedAt = at;
		}
	}

}
