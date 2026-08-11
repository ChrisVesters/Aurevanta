package com.cvesters.aurevanta.invitation;

import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRole;

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
 * An offer to join one organisation, sent to an address rather than to an account.
 *
 * <p>
 * The address is what the invitation is addressed to, and it is deliberately not a
 * {@link User}: the invitee may have no account at all, and the one they eventually sign
 * in with is decided when they accept. So this row names an email, and Step 10 turns it
 * into a membership.
 *
 * <p>
 * As with every emailed link, only a hash of the token is kept. There is no accessor for
 * the raw value because there is nowhere to get one from — {@code InvitationService}
 * returns it once, at the moment it goes into the message.
 */
@Entity
@Table(name = "invitations")
public class Invitation {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@Column(nullable = false, length = 320)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invited_by", nullable = false)
	private User invitedBy;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private InvitationStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	protected Invitation() {
		// for JPA
	}

	public Invitation(Tenant tenant, String email, UserRole role, User invitedBy, String tokenHash, Instant expiresAt,
			Instant createdAt) {
		this.tenant = tenant;
		this.email = email;
		this.role = role;
		this.invitedBy = invitedBy;
		this.tokenHash = tokenHash;
		this.status = InvitationStatus.PENDING;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	/**
	 * Puts a new link behind an invitation that has run out of time, in place.
	 *
	 * <p>
	 * The row stays because the unique index reserves one live invitation per address per
	 * organisation, and an expired one still holds that slot: inserting a second would
	 * trip the constraint over an invitation that is no longer a way in. Everything the
	 * invitation says is re-stated rather than kept, since whoever is sending it now may
	 * not be who sent it last and may be offering a different role.
	 */
	public void renew(UserRole role, User invitedBy, String tokenHash, Instant expiresAt, Instant at) {
		this.role = role;
		this.invitedBy = invitedBy;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = at;
	}

	/**
	 * Withdraws the offer, freeing the slot the partial unique index reserves so the same
	 * address can be invited again later.
	 *
	 * <p>
	 * The row stays rather than being deleted: an invitation that was deliberately
	 * withdrawn is a different answer to the person still holding the link than one that
	 * was never sent, and only a surviving row can tell them so.
	 */
	public void revoke() {
		this.status = InvitationStatus.REVOKED;
	}

	/**
	 * Whether the link has run out of time.
	 *
	 * <p>
	 * Says nothing about {@link #getStatus()}, because the only caller has already asked
	 * for a {@code PENDING} row: an expired invitation is still pending, which is exactly
	 * why the two questions have to be asked separately.
	 */
	public boolean hasExpired(Instant moment) {
		return !this.expiresAt.isAfter(moment);
	}

	public UUID getId() {
		return id;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public String getEmail() {
		return email;
	}

	public UserRole getRole() {
		return role;
	}

	public User getInvitedBy() {
		return invitedBy;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public InvitationStatus getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

}
