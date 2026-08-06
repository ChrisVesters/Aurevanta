package eu.sonetas.aurevanta.auth;

import org.springframework.http.HttpStatus;

/**
 * Raised for both an unknown email and a wrong password. Deliberately undifferentiated:
 * distinguishing them would let anyone probe which addresses hold accounts.
 */
public class InvalidCredentialsException extends AuthProblemException {

	public InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, "Authentication failed", "invalid_credentials",
				"Email or password is incorrect");
	}

}
