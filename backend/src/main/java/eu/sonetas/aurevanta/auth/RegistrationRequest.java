package eu.sonetas.aurevanta.auth;

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
}
