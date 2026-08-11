package com.cvesters.aurevanta.invitation;

/**
 * Where an invitation has got to.
 *
 * <p>
 * Only {@link #PENDING} is reachable in this step; Step 10 adds accepting and revoking.
 * The distinction is not decoration even so — the unique index that allows one live
 * invitation per address per organisation is scoped to {@code PENDING}, so an address
 * that has already joined and left, or been un-invited, can be invited again.
 */
public enum InvitationStatus {

	/** Sent, and still a way in until it is used, revoked, or expires. */
	PENDING,

	/** Spent: the invitee holds the membership it offered. */
	ACCEPTED,

	/** Withdrawn by an owner before it was used. */
	REVOKED

}
