package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The invitation ran out of time before anybody acted on it.
 *
 * <p>
 * Told apart from {@link InvitationRevokedException} and from a link that was never
 * issued, which the rest of this application deliberately does not do — the enumeration
 * risk those refusals guard against does not arise here, since holding a 256-bit token
 * means having received it, and what the visitor should do next differs in each case. An
 * expired invitation can be sent again; a withdrawn one cannot.
 */
public class InvitationExpiredException extends ApiProblemException {

	public InvitationExpiredException() {
		super(HttpStatus.BAD_REQUEST, "Invitation expired", "invitation_expired",
				"That invitation has expired. Ask whoever invited you to send another");
	}

}
