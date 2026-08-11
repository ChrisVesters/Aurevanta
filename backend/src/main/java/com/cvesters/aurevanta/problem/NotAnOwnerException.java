package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The caller belongs to the organisation but may not administer it.
 *
 * <p>
 * Distinct from {@link NotAMemberException}, which says the caller has no standing here
 * at all. Both are safe to distinguish: somebody already inside an organisation learns
 * nothing from being told which of the two they are.
 */
public class NotAnOwnerException extends ApiProblemException {

	public NotAnOwnerException() {
		super(HttpStatus.FORBIDDEN, "Not an owner", "not_an_owner", "Only an owner of this organisation may do that");
	}

}
