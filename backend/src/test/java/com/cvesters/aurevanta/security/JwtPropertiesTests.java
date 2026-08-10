package com.cvesters.aurevanta.security;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.security.JwtProperties;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTests {

	@Test
	void fallsBackToTheDefaultIssuerWhenUnset() {
		assertThat(new JwtProperties("secret", null, Duration.ofHours(1)).issuer()).isEqualTo("aurevanta");
	}

	@Test
	void fallsBackToTheDefaultIssuerWhenBlank() {
		assertThat(new JwtProperties("secret", "  ", Duration.ofHours(1)).issuer()).isEqualTo("aurevanta");
	}

	@Test
	void fallsBackToADefaultTokenLifetimeWhenUnset() {
		assertThat(new JwtProperties("secret", "aurevanta", null).accessTokenTtl()).isEqualTo(Duration.ofHours(12));
	}

	@Test
	void keepsConfiguredValues() {
		JwtProperties properties = new JwtProperties("secret", "elsewhere", Duration.ofMinutes(30));

		assertThat(properties.issuer()).isEqualTo("elsewhere");
		assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
	}

	@Test
	void reportsNoSecretWhenUnsetOrBlank() {
		assertThat(new JwtProperties(null, null, null).hasSecret()).isFalse();
		assertThat(new JwtProperties("   ", null, null).hasSecret()).isFalse();
	}

	@Test
	void reportsASecretWhenSet() {
		assertThat(new JwtProperties("a-secret", null, null).hasSecret()).isTrue();
	}

}
