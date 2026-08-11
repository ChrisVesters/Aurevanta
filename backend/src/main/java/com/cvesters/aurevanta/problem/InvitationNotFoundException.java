package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * No outstanding invitation with that identifier in the caller's organisation.
 *
 * <p>
 * One answer for "there is no such invitation", "it belongs to another organisation" and
 * "it has already been accepted or withdrawn". The first two must not be told apart, or
 * an owner could learn which identifiers exist elsewhere; the third is folded in because
 * the caller can see their own outstanding invitations and the list will have moved on
 * without them.
 */
public class InvitationNotFoundException extends ApiProblemException {

	public InvitationNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Invitation not found", "invitation_not_found",
				"There is no outstanding invitation with that identifier");
	}

}
