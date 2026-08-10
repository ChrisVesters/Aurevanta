package eu.sonetas.aurevanta.auth.verification;

import eu.sonetas.aurevanta.ratelimit.MailRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
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

	private final MailRateLimiter rateLimiter;

	VerificationController(EmailVerificationService verification, MailRateLimiter rateLimiter) {
		this.verification = verification;
		this.rateLimiter = rateLimiter;
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
	 * Rate limited by source and by recipient, since it is unauthenticated and it sends
	 * mail. Claimed before the address is looked up, so the limit counts requests rather
	 * than messages and cannot be used to tell a registered address from an unknown one.
	 */
	@PostMapping("/resend")
	@ResponseStatus(HttpStatus.ACCEPTED)
	void resend(@Valid @RequestBody ResendVerificationRequest request, HttpServletRequest incoming) {
		this.rateLimiter.claim(incoming.getRemoteAddr(), request.email());
		this.verification.resend(request.email());
	}

}
