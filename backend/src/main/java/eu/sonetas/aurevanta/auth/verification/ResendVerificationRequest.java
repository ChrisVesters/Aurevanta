package eu.sonetas.aurevanta.auth.verification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The address to send another confirmation link to.
 *
 * <p>
 * Validated for shape like every other address, which says nothing about whether it holds
 * an account — the endpoint answers identically either way.
 */
public record ResendVerificationRequest(@NotBlank @Email @Size(max = 320) String email) {

	public ResendVerificationRequest {
		email = (email != null) ? email.strip() : null;
	}

}
