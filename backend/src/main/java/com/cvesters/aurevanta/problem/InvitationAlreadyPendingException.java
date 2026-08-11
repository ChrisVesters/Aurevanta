package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A live invitation to this organisation is already sitting in that inbox.
 *
 * <p>
 * Refused rather than quietly sent again, because a second invitation would be a second
 * live link to the same organisation for the same address — one of which nobody would
 * ever account for. What the caller wants is to send the existing one again, which is a
 * different request against a row they can already see.
 *
 * <p>
 * An invitation that has <em>expired</em> does not land here: it is no longer a way in,
 * so it is renewed rather than treated as an obstacle.
 */
public class InvitationAlreadyPendingException extends ApiProblemException {

	public InvitationAlreadyPendingException() {
		super(HttpStatus.CONFLICT, "Invitation already sent", "invitation_already_pending",
				"That address has already been invited to this organisation");
	}

}
