package com.cvesters.aurevanta.ratelimit;

import java.time.Duration;
import java.util.Optional;

import com.cvesters.aurevanta.problem.TooManyRequestsException;

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
		RateLimiter.refuse(this.bySource.claim(sourceAddress));
		RateLimiter.refuse(this.byAddress.claim(RateLimiter.addressKey(emailAddress)));
	}

	/**
	 * Gives back the recipient's share of a claim that turned out to send nothing.
	 *
	 * <p>
	 * For a request refused by something decided without reference to the address at all
	 * — a handle somebody else already holds. The per-recipient budget bounds what can be
	 * dumped in one inbox, and a request that put nothing there has spent nothing of it.
	 * Without this, a refusal the product invites people to retry costs them the budget a
	 * password reset would later need.
	 *
	 * <p>
	 * <strong>The source keeps its claim.</strong> That budget bounds what one client can
	 * make this application do, and a refused registration still cost it a lookup and a
	 * bcrypt hash. Giving that back would make an endpoint that hashes a password
	 * unlimited to anyone willing to collide on a handle every time.
	 *
	 * <p>
	 * Only ever called for a refusal whose reason the caller has already been told, so it
	 * cannot become an answer to a question the blanket {@code 202}s exist to refuse.
	 * Nothing decided by looking the address up may be refunded.
	 */
	public void refundRecipient(String emailAddress) {
		this.byAddress.refund(RateLimiter.addressKey(emailAddress));
	}

	/**
	 * Addresses are matched the way every lookup matches them, or the limit would be
	 * spelling-sensitive when the mailbox it protects is not — three messages to
	 * {@code ada@acme.test} and three more to {@code ADA@acme.test} land in one inbox.
	 */

	/** Forgets every count, for tests that share an application context. */
	public void clear() {
		this.bySource.clear();
		this.byAddress.clear();
	}

}
