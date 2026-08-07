package eu.sonetas.aurevanta.auth.signin;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.sonetas.aurevanta.auth.AuthenticationResponse;

/**
 * The three ways signing in can end, now that an identity may hold no, one, or several
 * memberships. {@code outcome} is the discriminator and exactly one of the other two
 * fields is present; the absent one is omitted rather than sent as null, so a client
 * cannot mistake it for a session it may use.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignInResponse(SignInOutcome outcome, AuthenticationResponse session, IdentityResponse identity) {

	public enum SignInOutcome {

		/** Exactly one membership, so the organisation was chosen for the caller. */
		SIGNED_IN,

		/** Several memberships: the caller picks, then exchanges their identity token. */
		CHOOSE_ORGANISATION,

		/** None at all: nothing to exchange for until they are invited to one. */
		NO_ORGANISATION

	}

	public static SignInResponse signedIn(AuthenticationResponse session) {
		return new SignInResponse(SignInOutcome.SIGNED_IN, session, null);
	}

	public static SignInResponse choosing(IdentityResponse identity) {
		SignInOutcome outcome = identity.memberships().isEmpty() ? SignInOutcome.NO_ORGANISATION
				: SignInOutcome.CHOOSE_ORGANISATION;
		return new SignInResponse(outcome, null, identity);
	}

}
