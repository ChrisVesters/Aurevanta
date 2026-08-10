package eu.sonetas.aurevanta.auth.reset;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Both endpoints are unauthenticated, and have to be: somebody who has lost their
 * password cannot sign in to ask for a new one.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
class PasswordResetController {

	private final PasswordResetService reset;

	PasswordResetController(PasswordResetService reset) {
		this.reset = reset;
	}

	/**
	 * Always accepted, whoever asks, exactly as the confirmation resend is. Anyone can
	 * reach this without credentials, so an answer that distinguished a registered
	 * address from an unknown one would make it a way to ask who has an account.
	 * {@code 202} says only that the request was taken, which is the whole truth.
	 *
	 * <p>
	 * Unauthenticated and it sends mail, so it is an email-bombing vector until Step 7
	 * puts a rate limit in front of it.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	void request(@Valid @RequestBody PasswordResetRequest request) {
		this.reset.request(request.email());
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
		this.reset.confirm(request.token(), request.password());
	}

}
