package com.cvesters.aurevanta.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.user.User;

/**
 * Issues and redeems the emailed links that belong to an account.
 *
 * <p>
 * An invitation is the one emailed link that does not come from here: it is sent to
 * somebody who may hold no account, and every row in this table names one. It shares how
 * a token is made and stored — see {@link LinkTokens} — and nothing else.
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

	private final UserTokenRepository tokens;

	private final Clock clock;

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
		String value = LinkTokens.generate();
		Instant now = Instant.now(this.clock);
		Instant expiresAt = now.plus(ttl);
		this.tokens.save(new UserToken(user, purpose, LinkTokens.hash(value), expiresAt, now));
		return new SingleUseToken(value, expiresAt);
	}

	/**
	 * Spends a token and returns whose it was.
	 * @return the user, or empty if the token was unknown, expired, already spent, or
	 * issued for a different purpose — in which case nothing was changed
	 */
	@Transactional
	public Optional<User> consume(String rawToken, TokenPurpose purpose) {
		String tokenHash = LinkTokens.hash(rawToken);
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

}
