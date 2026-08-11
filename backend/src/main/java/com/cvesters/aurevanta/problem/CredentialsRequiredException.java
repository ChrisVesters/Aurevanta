package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Nobody holds the invited address yet, so accepting has to create an account — and an
 * account needs a name and a password the request did not carry.
 *
 * <p>
 * Its own refusal rather than a per-field validation failure, because the fields are only
 * required on one of the two ways through this endpoint: somebody accepting with an
 * account they already have sends no credentials at all, and rejecting them for it would
 * be a rule about a body they are right not to send.
 */
public class CredentialsRequiredException extends ApiProblemException {

	public CredentialsRequiredException() {
		super(HttpStatus.BAD_REQUEST, "Account details required", "credentials_required",
				"Choose a name and a password to finish setting up your account");
	}

}
