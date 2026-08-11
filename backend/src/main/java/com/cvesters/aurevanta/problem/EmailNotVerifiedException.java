package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The password was right, but the address behind the account has never been proved.
 *
 * <p>
 * Deliberately distinct from {@link InvalidCredentialsException}, and safe to be so: it
 * is only ever raised <em>after</em> the password has been checked, so the caller has
 * already shown they hold the account. Nothing is disclosed that they did not already
 * know. Reaching this before checking the password would turn it into a way to ask which
 * addresses are registered.
 *
 * <p>
 * Also raised when somebody whose own address is unconfirmed tries to write to a
 * stranger's inbox by inviting them. Under the gate that account cannot hold a token at
 * all, so it is unreachable rather than merely unlikely — which is the reason to state it
 * where it can be read, instead of leaving the rule resting on the gate never moving.
 */
public class EmailNotVerifiedException extends ApiProblemException {

	public EmailNotVerifiedException() {
		super(HttpStatus.FORBIDDEN, "Address not confirmed", "email_not_verified",
				"Confirm your email address before signing in");
	}

}
