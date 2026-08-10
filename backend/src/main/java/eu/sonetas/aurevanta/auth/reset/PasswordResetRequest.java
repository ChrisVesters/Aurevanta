package eu.sonetas.aurevanta.auth.reset;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The address to send a reset link to.
 *
 * <p>
 * Checked for shape, which says nothing about whether it holds an account — the endpoint
 * answers identically either way. What it does buy is that somebody who mistyped their
 * own address is told so, rather than being left waiting for a message that was never
 * going to arrive.
 */
public record PasswordResetRequest(@NotBlank @Email @Size(max = 320) String email) {

	public PasswordResetRequest {
		email = (email != null) ? email.strip() : null;
	}

}
