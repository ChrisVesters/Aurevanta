package com.cvesters.aurevanta.ratelimit;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import com.cvesters.aurevanta.auth.problem.TooManyRequestsException;

/**
 * Bounds how much mail the endpoints that need no credentials can be made to send.
 *
 * <p>
 * Every one of them exists for somebody who cannot sign in — confirming an address,
 * recovering a password — so none of them can be put behind authentication.
 * Unauthenticated and it sends mail to an address the caller chose is the definition of
 * an email-bombing vector, and this is what closes it.
 */
public class MailRateLimiter {

	private final RateLimiter bySource;

	private final RateLimiter byAddress;

	MailRateLimiter(RateLimiter bySource, RateLimiter byAddress) {
		this.bySource = bySource;
		this.byAddress = byAddress;
	}

	/**
	 * Counts one attempt, and refuses it if either limit is already spent.
	 *
	 * <p>
	 * The source is checked first so that a client which has exhausted its own allowance
	 * cannot go on spending other people's: were the address checked first, a flood from
	 * one machine would burn through the budget of every address it named, and the
	 * refusals would land on the victims rather than on the sender.
	 * @param sourceAddress where the request came from
	 * @param emailAddress who the message would go to
	 * @throws TooManyRequestsException if either limit is spent, carrying how long until
	 * it is not
	 */
	public void claim(String sourceAddress, String emailAddress) {
		refuseIfSpent(this.bySource.claim(sourceAddress));
		refuseIfSpent(this.byAddress.claim(normalise(emailAddress)));
	}

	private static void refuseIfSpent(Optional<Duration> refusal) {
		refusal.ifPresent((retryAfter) -> {
			throw new TooManyRequestsException(retryAfter);
		});
	}

	/**
	 * Addresses are matched the way every lookup matches them, or the limit would be
	 * spelling-sensitive when the mailbox it protects is not — three messages to
	 * {@code ada@acme.test} and three more to {@code ADA@acme.test} land in one inbox.
	 */
	private static String normalise(String emailAddress) {
		return emailAddress.strip().toLowerCase(Locale.ROOT);
	}

	/** Forgets every count, for tests that share an application context. */
	public void clear() {
		this.bySource.clear();
		this.byAddress.clear();
	}

}
