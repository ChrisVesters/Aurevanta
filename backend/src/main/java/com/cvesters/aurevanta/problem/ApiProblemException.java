package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A failure the API reports as an RFC 9457 problem document.
 *
 * <p>
 * Each subclass carries its own status, title and machine-readable code, so adding a
 * failure is one new class rather than a class plus another branch in
 * {@link ApiExceptionHandler}.
 *
 * <p>
 * This package sits beside the features rather than inside one because the vocabulary is
 * shared: {@code ratelimit} refuses on behalf of whatever it guards, and invitations
 * report failures of their own. Every {@code code} this API can publish is declared here,
 * so the whole contract can be read in one directory.
 */
public abstract class ApiProblemException extends RuntimeException {

	private final HttpStatus status;

	private final String title;

	private final String code;

	protected ApiProblemException(HttpStatus status, String title, String code, String detail) {
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
