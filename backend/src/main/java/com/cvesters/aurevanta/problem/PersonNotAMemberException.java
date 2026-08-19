package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A resource was named after somebody who is not in this organisation.
 *
 * <p>
 * <strong>Distinct from {@link NotAMemberException}, which is about the caller.</strong>
 * One says <em>you may not be here</em> and this says <em>they are not</em>, and a single
 * code for both would leave somebody re-authenticating over a mistyped colleague.
 *
 * <p>
 * Not a disclosure: a member may already list everybody in their own organisation, so
 * this says nothing they could not read from {@code GET /api/members}. What it stops is a
 * pool pointing at an account in somebody else's organisation — which would be the one
 * place in this schema where a tenant-owned row named a stranger.
 */
public class PersonNotAMemberException extends ApiProblemException {

	public PersonNotAMemberException() {
		super(HttpStatus.BAD_REQUEST, "Person not a member", "person_not_a_member",
				"That person does not belong to this organisation");
	}

}
