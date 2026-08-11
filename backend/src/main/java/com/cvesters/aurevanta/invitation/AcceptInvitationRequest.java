package com.cvesters.aurevanta.invitation;

import com.cvesters.aurevanta.user.PasswordRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The account to create for somebody accepting an invitation who has none yet.
 *
 * <p>
 * Sent only on that path. An invitee who already holds an account signs in and accepts
 * with no body at all, and this is the reason the body is optional rather than the reason
 * these fields are: what they are asked for depends on which of the two they are, and
 * neither should be asked for anything the other needs.
 *
 * <p>
 * There is deliberately no email field. The address is the one the invitation was sent
 * to; letting the request name a different one would turn an emailed link into a way to
 * create an account for somebody else's address.
 *
 * @param password held to {@link PasswordRules}, the same bounds registration and reset
 * apply. A third place that sets a credential is a third chance for the weakest one to
 * decide the rule for everybody.
 */
public record AcceptInvitationRequest(@NotBlank @Size(max = 200) String displayName,

		@NotBlank @Size(min = PasswordRules.MINIMUM_LENGTH, max = PasswordRules.MAXIMUM_LENGTH) String password) {

	/**
	 * The password is left exactly as typed, for the reason given on
	 * {@code RegistrationRequest}: spaces are legitimate in a passphrase, and trimming
	 * would store one credential and compare another at sign-in.
	 */
	public AcceptInvitationRequest {
		displayName = (displayName != null) ? displayName.strip() : null;
	}

}
