package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The change would leave an organisation with nobody able to administer it.
 *
 * <p>
 * An organisation with no owner cannot be repaired from inside the product: nobody can
 * invite, nobody can promote, and the people still in it can only watch. Refusing the
 * last demotion or removal is the only point at which that is preventable, so it is
 * refused whoever asks — including an owner trying to remove themselves.
 */
public class LastOwnerException extends ApiProblemException {

	public LastOwnerException() {
		super(HttpStatus.CONFLICT, "Last owner", "last_owner", "An organisation must always have at least one owner");
	}

}
