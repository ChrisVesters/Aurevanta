package com.cvesters.aurevanta.invitation;

import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.user.UserRole;

/**
 * An invitation as the organisation that sent it sees it.
 *
 * <p>
 * Carries no token, and cannot: only the hash was kept, and the raw value went into the
 * message. An owner who wants the invitee to have another link asks for it to be sent
 * again rather than reading one out of a response.
 *
 * @param id what a later revoke or resend names
 * @param expiresAt when the link stops working, so the sender can say when it will need
 * renewing rather than discovering it from a recipient who could not get in
 */
public record InvitationResponse(UUID id, String email, UserRole role, InvitationStatus status, Instant expiresAt,
		Instant createdAt) {

	static InvitationResponse of(Invitation invitation) {
		return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole(),
				invitation.getStatus(), invitation.getExpiresAt(), invitation.getCreatedAt());
	}

}
