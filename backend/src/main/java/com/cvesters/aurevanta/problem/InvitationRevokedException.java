package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The invitation was withdrawn before it was used.
 *
 * <p>
 * Distinct from {@link InvitationExpiredException} because the advice is opposite:
 * somebody decided this person should not join, so "ask for another" is the one thing not
 * to suggest.
 */
public class InvitationRevokedException extends ApiProblemException {

	public InvitationRevokedException() {
		super(HttpStatus.BAD_REQUEST, "Invitation withdrawn", "invitation_revoked",
				"That invitation is no longer available");
	}

}
