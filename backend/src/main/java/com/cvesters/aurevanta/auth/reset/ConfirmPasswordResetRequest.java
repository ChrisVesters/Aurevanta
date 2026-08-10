package com.cvesters.aurevanta.auth.reset;

import com.cvesters.aurevanta.user.PasswordRules;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A reset link and the password to put behind it.
 *
 * @param token bounded generously rather than exactly, for the reason given on
 * {@code VerifyEmailRequest}
 * @param password held to {@link PasswordRules}, the same bounds registration applies.
 * Anything looser here would let this endpoint set a password that could not have been
 * registered, which is the whole rule quietly rewritten by its weakest caller.
 */
public record ConfirmPasswordResetRequest(@NotBlank @Size(max = 200) String token,

		@NotBlank @Size(min = PasswordRules.MINIMUM_LENGTH, max = PasswordRules.MAXIMUM_LENGTH) String password) {

	/**
	 * Strips the token, which is copied out of a mail client as often as it is clicked.
	 *
	 * <p>
	 * The password is left exactly as typed, for the reason given on
	 * {@code RegistrationRequest}: spaces are legitimate in a passphrase, and trimming
	 * here would store one credential and compare another at sign-in.
	 */
	public ConfirmPasswordResetRequest {
		token = (token != null) ? token.strip() : null;
	}

}
