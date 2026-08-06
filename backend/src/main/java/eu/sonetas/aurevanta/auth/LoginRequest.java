package eu.sonetas.aurevanta.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials for an existing account. No organisation is named: the email identifies one
 * account, and that account's tenant is read from the database, never from the request.
 */
public record LoginRequest(@NotBlank @Size(max = 320) String email, @NotBlank @Size(max = 72) String password) {
}
