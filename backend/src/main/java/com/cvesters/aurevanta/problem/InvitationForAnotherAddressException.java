package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The caller is signed in as somebody other than the person who was invited.
 *
 * <p>
 * Refused rather than quietly attached to whoever happens to be signed in: a shared
 * computer, or a link forwarded to a colleague, would otherwise put the membership on the
 * wrong identity — and the person who was actually invited would have nothing to show for
 * their invitation but a spent link.
 */
public class InvitationForAnotherAddressException extends ApiProblemException {

	public InvitationForAnotherAddressException() {
		super(HttpStatus.FORBIDDEN, "Invitation for another address", "invitation_for_another_address",
				"This invitation was sent to a different address. Sign out and accept it as that person");
	}

}
