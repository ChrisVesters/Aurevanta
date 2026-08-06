package eu.sonetas.aurevanta.security;

import java.util.List;
import java.util.UUID;

import eu.sonetas.aurevanta.user.UserRole;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Turns a verified token into the {@link AuthenticatedUser} the rest of the application
 * works with. A token that carries claims this application never issues is rejected
 * rather than defaulted, because guessing a tenant would breach isolation.
 */
final class AuthenticatedUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		AuthenticatedUser principal = new AuthenticatedUser(uuidClaim(jwt.getSubject(), "sub"),
				uuidClaim(jwt.getClaimAsString(TokenClaims.TENANT_ID), TokenClaims.TENANT_ID),
				requireClaim(jwt.getClaimAsString(TokenClaims.EMAIL), TokenClaims.EMAIL), role(jwt));
		return new AurevantaAuthenticationToken(principal, jwt,
				List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
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
