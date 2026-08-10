package eu.sonetas.aurevanta.auth.verification;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Both endpoints are unauthenticated, and have to be: the person who needs them is
 * exactly the person who cannot sign in yet.
 */
@RestController
@RequestMapping("/api/auth/verify-email")
class VerificationController {

	private final EmailVerificationService verification;

	VerificationController(EmailVerificationService verification) {
		this.verification = verification;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void verify(@Valid @RequestBody VerifyEmailRequest request) {
		this.verification.verify(request.token());
	}

	/**
	 * Always accepted, whoever asks. A different answer for an address that holds an
	 * account would make this a way to ask who is registered, and it is reachable without
	 * credentials by anyone. {@code 202} says only that the request was taken, which is
	 * the whole truth: what happens next is decided without telling the caller.
	 *
	 * <p>
	 * Unauthenticated and it sends mail, so it is an email-bombing vector until Step 7
	 * puts a rate limit in front of it.
	 */
	@PostMapping("/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void resend(@Valid @RequestBody ResendVerificationRequest request) {
		this.verification.resend(request.email());
	}

}
