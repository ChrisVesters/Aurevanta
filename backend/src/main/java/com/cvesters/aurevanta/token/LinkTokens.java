package com.cvesters.aurevanta.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * How every token this application emails is made, and the only form of one it ever
 * writes down.
 *
 * <p>
 * {@link SingleUseTokenService} backs the links that belong to an account — confirming an
 * address, resetting a password — with a row per token. An invitation cannot use that
 * table, because it is sent to somebody who may have no account at all while
 * {@code user_tokens.user_id} is not null, so it carries a {@code token_hash} of its own.
 * What the two share is exactly this: 32 random bytes, base64url, stored as hex SHA-256.
 * Stated once so a second way of minting a link cannot quietly be a weaker one.
 */
public final class LinkTokens {

	/** 256 bits, so a token cannot be found by guessing however long anyone tries. */
	private static final int TOKEN_BYTES = 32;

	private static final String HASH_ALGORITHM = "SHA-256";

	private static final SecureRandom RANDOM = new SecureRandom();

	private LinkTokens() {
	}

	/**
	 * A fresh token in the clear. The caller has one chance to put it in a message:
	 * nothing keeps it, and {@link #hash} is not reversible.
	 */
	public static String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		// URL-safe and unpadded, because this ends up in a link somebody clicks.
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/**
	 * Hex-encoded SHA-256 of the raw token, which is the only form ever stored.
	 *
	 * <p>
	 * Unsalted on purpose, and not the {@code PasswordEncoder}: redemption has to find a
	 * row <em>by</em> hash, which a salted bcrypt would turn into a table scan, and the
	 * reason to be slow — guessing a low-entropy human secret — does not apply to 32
	 * bytes from a cryptographic generator.
	 */
	public static String hash(String rawToken) {
		return HexFormat.of().formatHex(digest(HASH_ALGORITHM).digest(rawToken.getBytes(StandardCharsets.UTF_8)));
	}

	/**
	 * The algorithm is a parameter so that the failure below is reachable from a test.
	 * Every Java platform is required to provide SHA-256, so in production this never
	 * throws — but a hash that silently became something else would make every previously
	 * issued token unredeemable, which is worth failing loudly over rather than assuming.
	 */
	static MessageDigest digest(String algorithm) {
		try {
			return MessageDigest.getInstance(algorithm);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(algorithm + " is not available", ex);
		}
	}

}
