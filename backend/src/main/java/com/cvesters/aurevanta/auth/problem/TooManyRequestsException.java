package com.cvesters.aurevanta.auth.problem;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/**
 * The caller has asked for too much mail, too quickly.
 *
 * <p>
 * Carries how long to wait, which the handler also puts in {@code Retry-After}. That is
 * the one thing a refusal here owes the caller: without it a client can only guess, and a
 * client that guesses badly retries in a loop — turning a limit meant to reduce traffic
 * into a reason for more of it.
 */
public class TooManyRequestsException extends AuthProblemException {

	private final Duration retryAfter;

	public TooManyRequestsException(Duration retryAfter) {
		super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests", "too_many_requests",
				"Too many requests were made. Wait a little and try again");
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return this.retryAfter;
	}

}
