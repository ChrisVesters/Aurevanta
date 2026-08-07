package eu.sonetas.aurevanta.security;

import java.time.Duration;
import java.time.Instant;

import eu.sonetas.aurevanta.membership.Membership;
import eu.sonetas.aurevanta.user.User;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Mints the two kinds of token this application accepts. */
@Service
public class AccessTokenService {

	private final JwtEncoder encoder;

	private final JwtProperties properties;

	AccessTokenService(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	/**
	 * Issues a token for one membership, pinning both the organisation and the role held
	 * in it at issue time so a caller cannot later claim to act for another one.
	 */
	public IssuedToken issue(Membership membership) {
		User user = membership.getUser();
		return encode(claims(user).claim(TokenClaims.TOKEN_TYPE, TokenClaims.ACCESS)
			.claim(TokenClaims.TENANT_ID, membership.getTenant().getId().toString())
			.claim(TokenClaims.ROLE, membership.getRole().name()));
	}

	/**
	 * Issues a token that names a person and no organisation, for a caller who has not
	 * chosen one — or has none to choose. It is strictly weaker than an access token: it
	 * reaches only the membership list and the exchange endpoint, so it carries the same
	 * lifetime without widening anything.
	 */
	public IssuedToken issueIdentityToken(User user) {
		return encode(claims(user).claim(TokenClaims.TOKEN_TYPE, TokenClaims.IDENTITY));
	}

	private JwtClaimsSet.Builder claims(User user) {
		return JwtClaimsSet.builder()
			.issuer(this.properties.issuer())
			.subject(user.getId().toString())
			.claim(TokenClaims.EMAIL, user.getEmail())
			.claim(TokenClaims.DISPLAY_NAME, user.getDisplayName());
	}

	private IssuedToken encode(JwtClaimsSet.Builder claims) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(this.properties.accessTokenTtl());
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		String value = this.encoder
			.encode(JwtEncoderParameters.from(header, claims.issuedAt(now).expiresAt(expiresAt).build()))
			.getTokenValue();
		return new IssuedToken(value, expiresAt, this.properties.accessTokenTtl());
	}

	/** An issued token and when it stops being accepted. */
	public record IssuedToken(String value, Instant expiresAt, Duration ttl) {
	}

}
