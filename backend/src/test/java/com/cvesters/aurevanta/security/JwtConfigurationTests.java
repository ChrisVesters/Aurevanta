package com.cvesters.aurevanta.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

import com.cvesters.aurevanta.security.JwtConfiguration;
import com.cvesters.aurevanta.security.JwtProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the configured-secret path in particular: the application only takes it in
 * production, where a failure is least convenient to discover.
 */
class JwtConfigurationTests {

	private static final String SECRET = "a-secret-long-enough-to-sign-with";

	private final JwtConfiguration configuration = new JwtConfiguration();

	@Test
	void usesTheConfiguredSecretAsTheSigningKey() {
		SecretKey key = this.configuration.jwtSigningKey(properties(SECRET));

		assertThat(key.getEncoded()).isEqualTo(SECRET.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void refusesToStartWithASecretTooShortToSignSafely() {
		assertThatThrownBy(() -> this.configuration.jwtSigningKey(properties("too-short")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("at least " + JwtProperties.MINIMUM_SECRET_LENGTH);
	}

	@Test
	void generatesAKeyOfUsableLengthWhenNoSecretIsConfigured() {
		SecretKey key = this.configuration.jwtSigningKey(properties(null));

		assertThat(key.getEncoded()).hasSize(JwtProperties.MINIMUM_SECRET_LENGTH);
	}

	@Test
	void generatesADifferentKeyEachStartWhenNoSecretIsConfigured() {
		SecretKey first = this.configuration.jwtSigningKey(properties(null));
		SecretKey second = this.configuration.jwtSigningKey(properties(null));

		assertThat(first.getEncoded()).isNotEqualTo(second.getEncoded());
	}

	@Test
	void acceptsATokenItJustIssued() {
		SecretKey key = this.configuration.jwtSigningKey(properties(SECRET));
		JwtDecoder decoder = this.configuration.jwtDecoder(key, properties(SECRET));

		String token = sign(key, claims("aurevanta", Instant.now().plus(Duration.ofMinutes(5))));

		assertThat(decoder.decode(token).getSubject()).isEqualTo("ada");
	}

	@Test
	void rejectsATokenSignedWithADifferentKey() {
		JwtDecoder decoder = this.configuration.jwtDecoder(this.configuration.jwtSigningKey(properties(SECRET)),
				properties(SECRET));
		SecretKey other = this.configuration.jwtSigningKey(properties("another-secret-of-sufficient-length"));

		String token = sign(other, claims("aurevanta", Instant.now().plus(Duration.ofMinutes(5))));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsATokenFromAnotherIssuer() {
		SecretKey key = this.configuration.jwtSigningKey(properties(SECRET));
		JwtDecoder decoder = this.configuration.jwtDecoder(key, properties(SECRET));

		String token = sign(key, claims("somewhere-else", Instant.now().plus(Duration.ofMinutes(5))));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsAnExpiredToken() {
		SecretKey key = this.configuration.jwtSigningKey(properties(SECRET));
		JwtDecoder decoder = this.configuration.jwtDecoder(key, properties(SECRET));

		String token = sign(key, claims("aurevanta", Instant.now().minus(Duration.ofMinutes(5))));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	private String sign(SecretKey key, JwtClaimsSet claims) {
		JwtEncoder encoder = this.configuration.jwtEncoder(key);
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private JwtClaimsSet claims(String issuer, Instant expiresAt) {
		return JwtClaimsSet.builder()
			.issuer(issuer)
			.subject("ada")
			.issuedAt(expiresAt.minus(Duration.ofHours(1)))
			.expiresAt(expiresAt)
			.build();
	}

	private JwtProperties properties(String secret) {
		return new JwtProperties(secret, "aurevanta", Duration.ofHours(12));
	}

}
