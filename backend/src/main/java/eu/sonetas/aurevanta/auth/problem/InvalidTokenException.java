package eu.sonetas.aurevanta.auth.problem;

import org.springframework.http.HttpStatus;

/**
 * An emailed link did not work.
 *
 * <p>
 * One answer for every reason — unknown, expired, already used, issued for something else
 * — because the holder of a link has no use for the difference and telling them would
 * confirm which tokens exist. What a client should offer instead is a new link, which
 * costs nothing to ask for.
 */
public class InvalidTokenException extends AuthProblemException {

	public InvalidTokenException() {
		super(HttpStatus.BAD_REQUEST, "Link no longer works", "invalid_token",
				"That link has expired or has already been used");
	}

}
