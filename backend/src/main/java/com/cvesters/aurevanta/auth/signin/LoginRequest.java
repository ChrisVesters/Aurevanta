package com.cvesters.aurevanta.auth.signin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials for an existing account. No organisation is named: the email identifies one
 * account, and that account's tenant is read from the database, never from the request.
 *
 * <p>
 * The address is checked for shape here as well as at registration. That is not the same
 * as revealing whether it holds an account: the constraint answers a question the caller
 * could answer themselves about what they typed, so it leaks nothing, while sending a
 * typo through to the credential check would answer "email or password is incorrect" and
 * set someone hunting for a password problem that does not exist. Which addresses are
 * registered stays hidden by {@code InvalidCredentialsException}, which is deliberately
 * the same for an unknown address and a wrong password.
 */
public record LoginRequest(@NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 72) String password) {

	/**
	 * Strips the address before validation sees it, for the reason given on
	 * {@code RegistrationRequest}: {@code @Email} rejects a padded address outright, so
	 * trimming afterwards would be too late to help anyone who pasted one.
	 *
	 * <p>
	 * The password is left exactly as typed, or sign-in would compare something other
	 * than what was registered.
	 */
	public LoginRequest {
		email = (email != null) ? email.strip() : null;
	}

}
