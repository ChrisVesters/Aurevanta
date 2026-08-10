package eu.sonetas.aurevanta.auth.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The token out of a confirmation link.
 *
 * @param token bounded generously rather than exactly: a token is 43 characters, but
 * pinning that here would turn a future change of token length into a puzzle.
 */
public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {

	public VerifyEmailRequest {
		token = (token != null) ? token.strip() : null;
	}

}
