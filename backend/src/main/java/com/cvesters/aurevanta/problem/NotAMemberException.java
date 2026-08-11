package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The caller asked for a token scoped to an organisation they do not belong to.
 *
 * <p>
 * Reported the same way whether the organisation exists or not, because the lookup is by
 * membership: an attacker learns only that they are not in it, which they knew.
 */
public class NotAMemberException extends ApiProblemException {

	public NotAMemberException() {
		super(HttpStatus.FORBIDDEN, "Not a member", "not_a_member", "You do not belong to that organisation");
	}

}
