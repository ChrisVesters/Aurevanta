package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The invited address already holds an account, so accepting means signing in first.
 *
 * <p>
 * The invitation token proves control of a mailbox, which is not the same as proving you
 * are the person whose account that mailbox registered. Attaching a membership to
 * somebody else's identity on the strength of an emailed link would be a way into their
 * account by way of their inbox, so the account holder authenticates and only then
 * accepts.
 *
 * <p>
 * This does tell the holder of the link that the address has an account. They already
 * hold a token emailed to it, and the alternative is a flow with no way forward for
 * anybody who already has one.
 */
public class SignInRequiredException extends ApiProblemException {

	public SignInRequiredException() {
		super(HttpStatus.UNAUTHORIZED, "Sign in to accept", "sign_in_required",
				"That address already has an account. Sign in to accept this invitation");
	}

}
