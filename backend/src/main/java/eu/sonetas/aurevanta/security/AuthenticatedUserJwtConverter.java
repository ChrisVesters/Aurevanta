package eu.sonetas.aurevanta.security;

import java.util.List;
import java.util.UUID;

import eu.sonetas.aurevanta.user.UserRole;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a verified token into the {@link AuthenticatedUser} the rest of the application
 * works with. A token that carries claims this application never issues is rejected
 * rather than defaulted, because guessing a tenant would breach isolation.
 *
 * <p>
 * The kind of token decides what comes out: an access token yields a principal pinned to
 * one organisation, an identity token one with no organisation at all and an authority
 * that reaches only the endpoints for choosing one.
 */
final class AuthenticatedUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		UUID userId = uuidClaim(jwt.getSubject(), "sub");
		String email = requireClaim(jwt.getClaimAsString(TokenClaims.EMAIL), TokenClaims.EMAIL);
		String tokenType = requireClaim(jwt.getClaimAsString(TokenClaims.TOKEN_TYPE), TokenClaims.TOKEN_TYPE);
		return switch (tokenType) {
			case TokenClaims.IDENTITY -> authentication(new AuthenticatedUser(userId, null, email, null), jwt,
					List.of(new SimpleGrantedAuthority(Authorities.IDENTITY)));
			case TokenClaims.ACCESS -> accessTokenAuthentication(jwt, userId, email);
			default -> throw new InvalidBearerTokenException(
					"Claim '" + TokenClaims.TOKEN_TYPE + "' is not a kind of token this application issues");
		};
	}

	private static AbstractAuthenticationToken accessTokenAuthentication(Jwt jwt, UUID userId, String email) {
		UUID tenantId = uuidClaim(jwt.getClaimAsString(TokenClaims.TENANT_ID), TokenClaims.TENANT_ID);
		UserRole role = role(jwt);
		return authentication(new AuthenticatedUser(userId, tenantId, email, role), jwt,
				List.of(new SimpleGrantedAuthority(Authorities.TENANT_SCOPED),
						new SimpleGrantedAuthority(Authorities.ROLE_PREFIX + role.name())));
	}

	private static AbstractAuthenticationToken authentication(AuthenticatedUser principal, Jwt jwt,
			List<GrantedAuthority> authorities) {
		return new AurevantaAuthenticationToken(principal, jwt, authorities);
	}

	private static UUID uuidClaim(String value, String claim) {
		try {
			return UUID.fromString(requireClaim(value, claim));
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidBearerTokenException("Claim '" + claim + "' is not a valid identifier", ex);
		}
	}

	private static UserRole role(Jwt jwt) {
		String value = requireClaim(jwt.getClaimAsString(TokenClaims.ROLE), TokenClaims.ROLE);
		try {
			return UserRole.valueOf(value);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidBearerTokenException("Claim '" + TokenClaims.ROLE + "' is not a known role", ex);
		}
	}

	private static String requireClaim(String value, String claim) {
		if (value == null || value.isBlank()) {
			throw new InvalidBearerTokenException("Token is missing the '" + claim + "' claim");
		}
		return value;
	}

}
