package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * No resource with that identifier belongs to the caller's organisation.
 *
 * <p>
 * One answer for "there is no such pool" and "it belongs to another organisation", as
 * {@link ProjectNotFoundException} and {@link WorkItemNotFoundException} are — telling
 * them apart would turn the endpoint into a way of discovering which identifiers exist
 * elsewhere, and a team's shape is a thing an organisation is entitled to keep to itself.
 */
public class ResourceNotFoundException extends ApiProblemException {

	public ResourceNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Resource not found", "resource_not_found",
				"No resource with that identifier belongs to this organisation");
	}

}
