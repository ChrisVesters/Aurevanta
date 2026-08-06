package eu.sonetas.aurevanta.auth;

import eu.sonetas.aurevanta.security.AccessTokenService.AccessToken;
import eu.sonetas.aurevanta.user.User;

/**
 * Issued by both registration and login, so a new user is signed in the moment they
 * register.
 *
 * @param accessToken send back as {@code Authorization: Bearer <accessToken>}
 */
public record AuthenticationResponse(String accessToken, String tokenType, long expiresInSeconds,
		AccountResponse account) {

	static AuthenticationResponse of(User user, AccessToken token) {
		return new AuthenticationResponse(token.value(), "Bearer", token.ttl().toSeconds(), AccountResponse.of(user));
	}

}
