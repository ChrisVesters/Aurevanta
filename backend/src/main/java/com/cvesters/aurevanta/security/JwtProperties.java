package com.cvesters.aurevanta.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the HMAC-signed access tokens issued at registration and login.
 *
 * @param secret signing key, at least 32 characters. Leave unset in development and a
 * random key is generated per start; it must be set explicitly anywhere tokens have to
 * survive a restart or be accepted by more than one instance.
 * @param issuer the {@code iss} claim, checked on every incoming token
 * @param accessTokenTtl how long an issued token stays valid
 */
@ConfigurationProperties("aurevanta.security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenTtl) {

	/** Shortest key HMAC-SHA256 accepts without weakening the signature. */
	public static final int MINIMUM_SECRET_LENGTH = 32;

	public JwtProperties {
		if (issuer == null || issuer.isBlank()) {
			issuer = "aurevanta";
		}
		if (accessTokenTtl == null) {
			accessTokenTtl = Duration.ofHours(12);
		}
	}

	boolean hasSecret() {
		return this.secret != null && !this.secret.isBlank();
	}

}
