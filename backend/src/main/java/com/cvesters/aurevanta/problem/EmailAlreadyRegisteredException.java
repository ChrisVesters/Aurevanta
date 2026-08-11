package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/** Raised when the email offered at registration already identifies an account. */
public class EmailAlreadyRegisteredException extends ApiProblemException {

	public EmailAlreadyRegisteredException() {
		super(HttpStatus.CONFLICT, "Email already registered", "email_already_registered",
				"That email address is already registered");
	}

}
