package com.cvesters.aurevanta.auth.problem;

import org.springframework.http.HttpStatus;

/** Raised when an organisation name holds no character a handle can be built from. */
public class UnusableOrganisationNameException extends AuthProblemException {

	public UnusableOrganisationNameException() {
		super(HttpStatus.BAD_REQUEST, "Invalid organisation name", "organisation_name_unusable",
				"Organisation name must contain at least one letter or digit");
	}

}
