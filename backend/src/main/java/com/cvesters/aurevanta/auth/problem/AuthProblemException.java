package com.cvesters.aurevanta.auth.problem;

import org.springframework.http.HttpStatus;

/**
 * A registration or sign-in failure the API reports as an RFC 9457 problem document.
 *
 * <p>
 * Each subclass carries its own status, title and machine-readable code, so adding a
 * failure is one new class rather than a class plus another branch in
 * {@link AuthExceptionHandler}.
 */
public abstract class AuthProblemException extends RuntimeException {

	private final HttpStatus status;

	private final String title;

	private final String code;

	protected AuthProblemException(HttpStatus status, String title, String code, String detail) {
		super(detail);
		this.status = status;
		this.title = title;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return this.status;
	}

	public String getTitle() {
		return this.title;
	}

	/** Stable identifier the client translates; never shown to a user as-is. */
	public String getCode() {
		return this.code;
	}

}
