package eu.sonetas.aurevanta.auth.registration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Signing up creates an organisation and its first user in one step; there is no separate
 * "create a workspace" stage afterwards.
 *
 * @param organisationName display name of the tenant being created
 * @param displayName how the registering user should be shown
 * @param email login identity, unique across the installation
 * @param password minimum 12 characters. The upper bound is bcrypt's: it hashes only the
 * first 72 bytes, so anything longer would be silently truncated.
 */
public record RegistrationRequest(

		@NotBlank @Size(max = 200) String organisationName,

		@NotBlank @Size(max = 200) String displayName,

		@NotBlank @Email @Size(max = 320) String email,

		@NotBlank @Size(min = 12, max = 72) String password) {

	/**
	 * Strips surrounding whitespace before anything else sees it, validation included.
	 * Trimming afterwards would be too late: {@code @Email} rejects a padded address
	 * outright, so pasting one out of a password manager would be answered with "enter a
	 * valid email address" about an address that is perfectly valid.
	 *
	 * <p>
	 * The password is deliberately left exactly as typed. Spaces are legitimate
	 * characters in a passphrase, and trimming would quietly change the credential —
	 * storing one thing at registration and comparing another at sign-in.
	 */
	public RegistrationRequest {
		organisationName = stripped(organisationName);
		displayName = stripped(displayName);
		email = stripped(email);
	}

	private static String stripped(String value) {
		return (value != null) ? value.strip() : null;
	}

}
