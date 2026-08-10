package com.cvesters.aurevanta.auth.reset;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.ratelimit.MailRateLimiter;

/**
 * Both endpoints are unauthenticated, and have to be: somebody who has lost their
 * password cannot sign in to ask for a new one.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
class PasswordResetController {

	private final PasswordResetService reset;

	private final MailRateLimiter rateLimiter;

	PasswordResetController(PasswordResetService reset, MailRateLimiter rateLimiter) {
		this.reset = reset;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Always accepted, whoever asks, exactly as the confirmation resend is. Anyone can
	 * reach this without credentials, so an answer that distinguished a registered
	 * address from an unknown one would make it a way to ask who has an account.
	 * {@code 202} says only that the request was taken, which is the whole truth.
	 *
	 * <p>
	 * Rate limited by source and by recipient, since it is unauthenticated and it sends
	 * mail. The limit is claimed before anything is looked up, so it counts requests
	 * rather than messages — a limit that only counted the addresses that turned out to
	 * exist would answer the question the {@code 202} is there to refuse.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	void request(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest incoming) {
		this.rateLimiter.claim(incoming.getRemoteAddr(), request.email());
		this.reset.request(request.email());
	}

	@PostMapping("/confirm")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
		this.reset.confirm(request.token(), request.password());
	}

}
