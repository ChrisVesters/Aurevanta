package eu.sonetas.aurevanta.auth;

import eu.sonetas.aurevanta.membership.Membership;
import eu.sonetas.aurevanta.security.AccessTokenService.IssuedToken;

/**
 * A session scoped to one organisation: the token to send back, and the account it acts
 * as. Returned by registration, by a sign-in that resolved to a single membership, and by
 * exchanging a token for another organisation.
 *
 * @param accessToken send back as {@code Authorization: Bearer <accessToken>}
 */
public record AuthenticationResponse(String accessToken, String tokenType, long expiresInSeconds,
		AccountResponse account) {

	static AuthenticationResponse of(Membership membership, IssuedToken token) {
		return new AuthenticationResponse(token.value(), "Bearer", token.ttl().toSeconds(),
				AccountResponse.of(membership));
	}

}
