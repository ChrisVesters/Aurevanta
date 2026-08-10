package com.cvesters.aurevanta.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.user.User;

/**
 * Issues and redeems the tokens behind emailed links, for every purpose that needs one.
 *
 * <p>
 * The guarantee is that a token works exactly once: {@link #consume} either returns the
 * person it belongs to <em>and</em> has spent it, or returns nothing at all. It says
 * nothing about <em>why</em> a token was refused — unknown, expired, already spent and
 * issued for something else are one answer — because a caller has no honest use for the
 * difference and reporting it would confirm which tokens exist.
 */
@Service
public class SingleUseTokenService {

	/** 256 bits, so a token cannot be found by guessing however long anyone tries. */
	private static final int TOKEN_BYTES = 32;

	private static final String HASH_ALGORITHM = "SHA-256";

	private final UserTokenRepository tokens;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	SingleUseTokenService(UserTokenRepository tokens, Clock clock) {
		this.tokens = tokens;
		this.clock = clock;
	}

	/**
	 * Creates a token for {@code user} and returns it in the clear, once. Only the hash
	 * is written, so a value not handed to the recipient here cannot be recovered.
	 */
	@Transactional
	public SingleUseToken issue(User user, TokenPurpose purpose, Duration ttl) {
		byte[] bytes = new byte[TOKEN_BYTES];
		this.random.nextBytes(bytes);
		// URL-safe and unpadded, because this ends up in a link somebody clicks.
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		Instant now = Instant.now(this.clock);
		Instant expiresAt = now.plus(ttl);
		this.tokens.save(new UserToken(user, purpose, hash(value), expiresAt, now));
		return new SingleUseToken(value, expiresAt);
	}

	/**
	 * Spends a token and returns whose it was.
	 * @return the user, or empty if the token was unknown, expired, already spent, or
	 * issued for a different purpose — in which case nothing was changed
	 */
	@Transactional
	public Optional<User> consume(String rawToken, TokenPurpose purpose) {
		String tokenHash = hash(rawToken);
		if (this.tokens.consume(tokenHash, purpose, Instant.now(this.clock)) == 0) {
			return Optional.empty();
		}
		return this.tokens.findUserByTokenHash(tokenHash);
	}

	/**
	 * Spends everything {@code user} still holds for {@code purpose}, so that nothing
	 * issued earlier outlives whatever has just happened.
	 *
	 * <p>
	 * Asking twice because the first message was slow leaves two live links in an inbox.
	 * Once either one has been acted on the other is no longer something the account
	 * holder needs, and leaving it working means a message read by somebody else later is
	 * still a way in — after the owner has already recovered and would have no reason to
	 * suspect it.
	 */
	@Transactional
	public void revokeAll(User user, TokenPurpose purpose) {
		this.tokens.consumeAllFor(user, purpose, Instant.now(this.clock));
	}

	/**
	 * Hex-encoded SHA-256 of the raw token, which is the only form ever written down.
	 * Unsalted on purpose: redemption has to find a row by hash, and the input is 32
	 * random bytes rather than a guessable secret, so neither a salt nor a slow function
	 * buys anything here.
	 */
	private static String hash(String rawToken) {
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
