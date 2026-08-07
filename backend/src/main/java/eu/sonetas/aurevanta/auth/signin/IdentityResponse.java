package eu.sonetas.aurevanta.auth.signin;

import java.util.List;

import eu.sonetas.aurevanta.membership.Membership;
import eu.sonetas.aurevanta.membership.MembershipSummary;
import eu.sonetas.aurevanta.security.AccessTokenService.IssuedToken;

/**
 * What a caller gets when sign-in cannot pick an organisation for them: a token that
 * names them but no organisation, and the organisations that token may be exchanged for.
 *
 * @param memberships most recently used first, so a client can offer a sensible default.
 * Empty for someone who belongs to nothing — a real state, reached by being removed from
 * your only organisation.
 */
public record IdentityResponse(String identityToken, String tokenType, long expiresInSeconds,
		List<MembershipSummary> memberships) {

	public static IdentityResponse of(IssuedToken token, List<Membership> memberships) {
		return new IdentityResponse(token.value(), "Bearer", token.ttl().toSeconds(),
				memberships.stream().map(MembershipSummary::of).toList());
	}

}
