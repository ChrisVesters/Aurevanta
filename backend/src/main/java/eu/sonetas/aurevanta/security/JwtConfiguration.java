package eu.sonetas.aurevanta.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

/** Wires the symmetric key used to sign and verify access tokens. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfiguration {

	private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		if (!properties.hasSecret()) {
			log.warn("No aurevanta.security.jwt.secret configured; generating a random signing key. "
					+ "Access tokens will be rejected after a restart and by any other instance. "
					+ "Set the property before running anywhere but a single developer machine.");
			byte[] generated = new byte[JwtProperties.MINIMUM_SECRET_LENGTH];
			new SecureRandom().nextBytes(generated);
			return new SecretKeySpec(generated, HMAC_ALGORITHM);
		}
		byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
		if (secret.length < JwtProperties.MINIMUM_SECRET_LENGTH) {
			throw new IllegalStateException("aurevanta.security.jwt.secret must be at least "
					+ JwtProperties.MINIMUM_SECRET_LENGTH + " characters; it is " + secret.length);
		}
		return new SecretKeySpec(secret, HMAC_ALGORITHM);
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
				new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}

}
