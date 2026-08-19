package com.cvesters.aurevanta.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.cvesters.aurevanta.problem.TooManyRequestsException;

/**
 * How often one key may do something, over a sliding window.
 *
 * <p>
 * Sliding rather than a fixed window that resets on the hour: a fixed one lets twice the
 * limit through across its boundary, which for a limit whose whole job is to bound how
 * much mail somebody receives is exactly the case worth getting right.
 *
 * <p>
 * <strong>State lives in this process and nowhere else.</strong> A second instance would
 * have its own counts, so the effective limit becomes the configured one multiplied by
 * the number of instances. That is fine while there is one; it stops being fine the
 * moment anything is scaled out, and the fix then is shared state rather than a bigger
 * number.
 */
public class RateLimiter {

	/**
	 * How many keys may accumulate before expired ones are cleared out. A sweep costs a
	 * pass over the map, so it is worth doing rarely and in bulk rather than on every
	 * call.
	 */
	private static final int SWEEP_ABOVE = 10_000;

	/** Never reported as zero: "try again in no time at all" is not an instruction. */
	private static final Duration MINIMUM_RETRY_AFTER = Duration.ofSeconds(1);

	private final int limit;

	private final Duration window;

	private final Clock clock;

	/**
	 * When each key acted, oldest first, with anything outside the window dropped as it
	 * is read. Bounded by the limit per key, so the memory one key can occupy is fixed.
	 */
	private final Map<String, Deque<Instant>> recent = new ConcurrentHashMap<>();

	public RateLimiter(int limit, Duration window, Clock clock) {
		this.limit = limit;
		this.window = window;
		this.clock = clock;
	}

	/**
	 * Records an attempt by {@code key} and says whether it may proceed.
	 *
	 * <p>
	 * Attempts are counted, not successes. A caller must therefore claim before it knows
	 * whether there is anything to do — which is the point for the endpoints this guards:
	 * counting only the requests that turned out to send mail would make the limit itself
	 * an answer to "does this address have an account?".
	 * @return empty if the attempt may proceed, or how long until it could succeed
	 */
	public Optional<Duration> claim(String key) {
		Instant now = Instant.now(this.clock);
		if (this.recent.size() > SWEEP_ABOVE) {
			forgetExpired(now);
		}
		// One atomic step per key: compute holds that bin, so two requests arriving
		// together cannot both read a count of limit - 1 and both be allowed through.
		Duration[] refusal = new Duration[1];
		this.recent.compute(key, (ignored, hits) -> {
			Deque<Instant> live = (hits != null) ? hits : new ArrayDeque<>();
			dropExpired(live, now);
			if (live.size() < this.limit) {
				live.addLast(now);
			}
			else {
				refusal[0] = retryAfter(live.peekFirst(), now);
			}
			return live;
		});
		return Optional.ofNullable(refusal[0]);
	}

	/**
	 * Whether {@code key} is already over its limit, recording nothing either way.
	 *
	 * <p>
	 * For the callers that count <em>failures</em> rather than attempts: a sign-in cannot
	 * know whether it was a failure until the password has been checked, so asking and
	 * recording have to be separate acts. Not atomic with the {@link #record} that
	 * follows it, which costs a handful of extra attempts under a race and nothing else —
	 * the point is to stop somebody making thousands, not to be exact about ten.
	 * @return empty if another attempt may proceed, or how long until one could
	 */
	public Optional<Duration> refusalFor(String key) {
		Instant now = Instant.now(this.clock);
		Duration[] refusal = new Duration[1];
		this.recent.computeIfPresent(key, (ignored, hits) -> {
			dropExpired(hits, now);
			if (hits.size() >= this.limit) {
				refusal[0] = retryAfter(hits.peekFirst(), now);
			}
			return hits.isEmpty() ? null : hits;
		});
		return Optional.ofNullable(refusal[0]);
	}

	/**
	 * Counts one attempt against {@code key}.
	 *
	 * <p>
	 * Nothing is added once the limit is reached, which is what makes this a limit on the
	 * <em>rate</em> rather than a ban that lengthens under fire. It matters most where a
	 * key is somebody else's account: were refused attempts counted, anyone willing to
	 * keep knocking could hold a stranger out of their own account indefinitely, and the
	 * harder they knocked the longer it would last. A window that simply expires cannot
	 * be used that way. Callers reinforce it by refusing before they record, so an
	 * attempt already over the limit never reaches here at all — this branch is for two
	 * attempts that pass the check together.
	 */
	public void record(String key) {
		Instant now = Instant.now(this.clock);
		if (this.recent.size() > SWEEP_ABOVE) {
			forgetExpired(now);
		}
		this.recent.compute(key, (ignored, hits) -> {
			Deque<Instant> live = (hits != null) ? hits : new ArrayDeque<>();
			dropExpired(live, now);
			if (live.size() < this.limit) {
				live.addLast(now);
			}
			return live;
		});
	}

	/** Forgets one key, for a caller whose attempt turned out to be legitimate. */
	public void forget(String key) {
		this.recent.remove(key);
	}

	/**
	 * Takes back the most recent attempt counted against {@code key}.
	 *
	 * <p>
	 * For a caller that had to claim before it could know whether there was anything to
	 * claim for. Attempts are counted rather than successes precisely so that a limit
	 * cannot be read as an answer, and that stays true here: what is given back is one
	 * request's own claim, decided by something the refusal already told the caller.
	 *
	 * <p>
	 * Silent if the key has since expired out of the window, which is the right outcome —
	 * there is nothing left to give back, and inventing a negative count would let a
	 * refunded request pay for a later one.
	 */
	public void refund(String key) {
		this.recent.computeIfPresent(key, (ignored, hits) -> {
			hits.pollLast();
			return hits.isEmpty() ? null : hits;
		});
	}

	/** When the oldest attempt still counted leaves the window, and a slot frees up. */
	/**
	 * Refuses if a claim came back spent, which is what both limiters do with one.
	 *
	 * <p>
	 * Here rather than in each of them because it is the shape of the answer rather than
	 * a policy either owns: {@link #claim} and {@link #refusalFor} report a refusal by
	 * returning how long is left, so that two of these can be composed before either
	 * throws — a request refused by the per-source budget must not also spend the
	 * per-recipient one.
	 */
	static void refuse(Optional<Duration> refusal) {
		refusal.ifPresent((retryAfter) -> {
			throw new TooManyRequestsException(retryAfter);
		});
	}

	/**
	 * One spelling of an address, and the reason it is stated once.
	 *
	 * <p>
	 * <strong>This is the key a budget is stored under.</strong> Two copies of it is two
	 * chances for one to start folding case differently from the other — and the failure
	 * would be silent and one-sided: a limiter keying on the raw address counts
	 * {@code Ada@acme.test} and {@code ada@acme.test} as two people, so the budget the
	 * other one is enforcing quietly stops being the budget anybody is subject to.
	 */
	static String addressKey(String emailAddress) {
		return emailAddress.strip().toLowerCase(Locale.ROOT);
	}

	private Duration retryAfter(Instant oldest, Instant now) {
		Duration remaining = Duration.between(now, oldest.plus(this.window));
		return remaining.compareTo(MINIMUM_RETRY_AFTER) < 0 ? MINIMUM_RETRY_AFTER : remaining;
	}

	private void dropExpired(Deque<Instant> hits, Instant now) {
		Instant cutoff = now.minus(this.window);
		while (!hits.isEmpty() && !hits.peekFirst().isAfter(cutoff)) {
			hits.removeFirst();
		}
	}

	/**
	 * Discards keys that can no longer refuse anything.
	 *
	 * <p>
	 * Each key is emptied and removed inside its own {@code computeIfPresent}, so a sweep
	 * never races a claim: the worst it can do is remove a key a moment before that key
	 * is recreated, which costs nothing because it held no live attempts either way.
	 */
	private void forgetExpired(Instant now) {
		this.recent.forEach((key, ignored) -> this.recent.computeIfPresent(key, (k, hits) -> {
			dropExpired(hits, now);
			return hits.isEmpty() ? null : hits;
		}));
	}

	/**
	 * Forgets every key. Exists for tests, which share one application context across
	 * cases and would otherwise let one case spend another's allowance.
	 */
	public void clear() {
		this.recent.clear();
	}

	/**
	 * How many keys are being tracked; used to prove the sweep actually releases them.
	 */
	int tracked() {
		return this.recent.size();
	}

}
