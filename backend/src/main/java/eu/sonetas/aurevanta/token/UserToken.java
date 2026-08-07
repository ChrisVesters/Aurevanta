package eu.sonetas.aurevanta.token;

import java.time.Instant;
import java.util.UUID;

import eu.sonetas.aurevanta.user.User;
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
 * One emailed link, stored as a hash of the token it carries.
 *
 * <p>
 * There is deliberately no accessor for the raw token, because there is nowhere to get
 * one from: {@link SingleUseTokenService} returns it once at issue and keeps only
 * {@link #getTokenHash()}. A row is spent when {@link #getConsumedAt()} stops being null.
 */
@Entity
@Table(name = "user_tokens")
public class UserToken {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private TokenPurpose purpose;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected UserToken() {
		// for JPA
	}

	UserToken(User user, TokenPurpose purpose, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.user = user;
		this.purpose = purpose;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	// Only what something actually reads. Redemption is a single conditional update and
	// returns the user directly, so no caller ever holds one of these — the accessors
	// below exist because the tests inspect what was written down.

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}

}
