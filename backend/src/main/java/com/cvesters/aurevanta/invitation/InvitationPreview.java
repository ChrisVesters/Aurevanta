package com.cvesters.aurevanta.invitation;

import com.cvesters.aurevanta.user.UserRole;

/**
 * What somebody holding an invitation link is shown before they act on it.
 *
 * <p>
 * Three fields, and deliberately no more. This is served without credentials to anyone
 * who has the link, so it must disclose nothing a member of the organisation would have
 * had to sign in to see — no identifier, no handle, no size, no member list. What it does
 * carry is what a person needs in order to decide: who is asking, what they are being
 * asked to join, and what they would be once they had.
 *
 * <p>
 * It also says nothing about the invited address — whether it already holds an account is
 * answered only when somebody actually tries to accept, and then only to them.
 */
public record InvitationPreview(String organisationName, String invitedBy, UserRole role) {

	static InvitationPreview of(Invitation invitation) {
		return new InvitationPreview(invitation.getTenant().getName(), invitation.getInvitedBy().getDisplayName(),
				invitation.getRole());
	}

}
