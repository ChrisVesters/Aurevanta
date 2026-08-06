package eu.sonetas.aurevanta.security;

import java.time.Duration;
import java.time.Instant;

import eu.sonetas.aurevanta.user.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Mints the access tokens handed out at registration and login. */
@Service
public class AccessTokenService {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	AccessTokenService(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	/**
	 * Issues a token identifying the user and, crucially, their tenant. The tenant is
	 * pinned at issue time so a caller cannot later claim to act for another one.
	 */
	public AccessToken issue(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(this.properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(this.properties.issuer())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.subject(user.getId().toString())
			.claim(TokenClaims.TENANT_ID, user.getTenant().getId().toString())
			.claim(TokenClaims.EMAIL, user.getEmail())
			.claim(TokenClaims.ROLE, user.getRole().name())
			.claim(TokenClaims.DISPLAY_NAME, user.getDisplayName())
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = this.encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(value, expiresAt, this.properties.accessTokenTtl());
	}

	/** An issued token and when it stops being accepted. */
	public record AccessToken(String value, Instant expiresAt, Duration ttl) {
	}

}
