package com.cvesters.aurevanta.auth.registration;

import com.cvesters.aurevanta.tenant.Slug;
import com.cvesters.aurevanta.user.PasswordRules;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Signing up creates an organisation and its first user in one step; there is no separate
 * "create a workspace" stage afterwards.
 *
 * @param organisationName display name of the tenant being created
 * @param organisationSlug the handle it will answer to, chosen rather than derived — see
 * {@code CreateOrganisationRequest}, which asks for the same pair
 * @param displayName how the registering user should be shown
 * @param email login identity, unique across the installation
 * @param password bounded by {@link PasswordRules}, which password reset shares — the two
 * places that set a credential must not be able to disagree about what one may be.
 */
public record RegistrationRequest(

		@NotBlank @Size(max = 200) String organisationName,

		@NotBlank @Pattern(regexp = Slug.PATTERN) @Size(min = Slug.MIN_LENGTH,
				max = Slug.MAX_LENGTH) String organisationSlug,

		@NotBlank @Size(max = 200) String displayName,

		@NotBlank @Email @Size(max = 320) String email,

		@NotBlank @Size(min = PasswordRules.MINIMUM_LENGTH, max = PasswordRules.MAXIMUM_LENGTH) String password) {

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
		organisationSlug = stripped(organisationSlug);
		displayName = stripped(displayName);
		email = stripped(email);
	}

	private static String stripped(String value) {
		return (value != null) ? value.strip() : null;
	}

}
