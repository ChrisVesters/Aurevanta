package com.cvesters.aurevanta.invitation;

import com.cvesters.aurevanta.user.UserRole;

/**
 * What somebody holding an invitation link is shown before they act on it.
 *
 * <p>
 * Four fields, and deliberately no more. This is served without credentials to anyone who
 * has the link, so it must disclose nothing a member of the organisation would have had
 * to sign in to see — no identifier, no handle, no size, no member list. What it does
 * carry is what a person needs in order to decide: who is asking, what they are being
 * asked to join, and what they would be once they had.
 *
 * <p>
 * {@code claimed} is the fourth, and it is the one thing said about the invited address:
 * whether an account already holds it, and so whether accepting means signing in or
 * making one. Without it the page has to guess from the visitor's own session, which is a
 * different question — somebody invited at an address they already have an account for
 * was asked to choose a display name and a password, and only told to sign in instead
 * after submitting them.
 *
 * <p>
 * It is one bit, and it is not the address itself. Reaching it takes the raw token, which
 * was mailed to that address and is stored here only as a hash, so the audience is the
 * recipient — who knows the answer — and anyone they forwarded the message to. That is
 * the disclosure being accepted, and it is the whole of it: nothing here names the
 * address, and an address cannot be tested against this endpoint without already holding
 * its link.
 */
public record InvitationPreview(String organisationName, String invitedBy, UserRole role, boolean claimed) {

	static InvitationPreview of(Invitation invitation, boolean claimed) {
		return new InvitationPreview(invitation.getTenant().getName(), invitation.getInvitedBy().getDisplayName(),
				invitation.getRole(), claimed);
	}

}
