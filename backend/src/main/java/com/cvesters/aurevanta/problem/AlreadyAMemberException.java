package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The address being invited already belongs to this organisation.
 *
 * <p>
 * Says nothing an owner did not already have: they can list their own members, so
 * confirming that one of them is a member discloses nothing across the boundary. It is
 * scoped to <em>this</em> organisation for that reason — whether the address holds an
 * account anywhere else is not something an invitation may be used to ask.
 */
public class AlreadyAMemberException extends ApiProblemException {

	public AlreadyAMemberException() {
		super(HttpStatus.CONFLICT, "Already a member", "already_a_member",
				"That address already belongs to this organisation");
	}

}
