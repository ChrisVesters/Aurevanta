package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * No dependency with that identifier belongs to the caller's organisation.
 *
 * <p>
 * One answer for "there is no such edge" and "it belongs to another organisation", as
 * {@link WorkItemNotFoundException} and {@link ProjectNotFoundException} are — and for
 * the same reason, since telling them apart would turn the endpoint into a way of
 * discovering which identifiers exist elsewhere.
 */
public class DependencyNotFoundException extends ApiProblemException {

	public DependencyNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Dependency not found", "dependency_not_found",
				"No dependency with that identifier belongs to this organisation");
	}

}
