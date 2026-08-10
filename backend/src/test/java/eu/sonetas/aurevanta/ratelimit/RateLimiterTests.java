package eu.sonetas.aurevanta.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The window itself, driven by a clock a test can move. Everything here is about the
 * property that matters — a key gets its allowance and no more — and about the boundary,
 * where a fixed window would quietly let through twice the limit.
 */
class RateLimiterTests {

	private static final Duration WINDOW = Duration.ofMinutes(15);

	private final MovableClock clock = new MovableClock(Instant.parse("2026-08-10T09:00:00Z"));

	private final RateLimiter limiter = new RateLimiter(3, WINDOW, this.clock);

	@Test
	void allowsUpToTheLimit() {
		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
	}

	@Test
	void refusesTheOneAfterTheLimit() {
		spend(3);

		assertThat(this.limiter.claim("ada@acme.test")).isPresent();
	}

	/** Without this a client can only guess, and a client that guesses badly retries. */
	@Test
	void saysHowLongUntilTheOldestAttemptLeavesTheWindow() {
		spend(3);
		this.clock.advanceBy(Duration.ofMinutes(5));

		assertThat(this.limiter.claim("ada@acme.test")).contains(Duration.ofMinutes(10));
	}

	/** Never zero: "try again in no time at all" is not something a client can act on. */
	@Test
	void neverAsksForAWaitOfNoTime() {
		spend(3);
		this.clock.advanceBy(WINDOW.minusMillis(1));

		assertThat(this.limiter.claim("ada@acme.test")).contains(Duration.ofSeconds(1));
	}

	@Test
	void allowsAgainOnceTheWindowHasPassed() {
		spend(3);
		this.clock.advanceBy(WINDOW.plusSeconds(1));

		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
	}

	/**
	 * Sliding, not fixed. Three attempts at the start of a window and three at the start
	 * of the next would be six inside twenty minutes if the count reset on a boundary —
	 * for a limit whose job is to bound what lands in somebody's inbox, that is the case
	 * worth proving.
	 */
	@Test
	void releasesOneSlotAtATimeAsAttemptsAgeOut() {
		this.limiter.claim("ada@acme.test");
		this.clock.advanceBy(Duration.ofMinutes(10));
		this.limiter.claim("ada@acme.test");
		this.limiter.claim("ada@acme.test");

		// The first attempt ages out here; the two from minute ten do not.
		this.clock.advanceBy(Duration.ofMinutes(6));

		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.claim("ada@acme.test")).isPresent();
	}

	@Test
	void countsEachKeySeparately() {
		spend(3);

		assertThat(this.limiter.claim("grace@acme.test")).isEmpty();
	}

	/**
	 * Two requests arriving together must not both read the same spare slot and both take
	 * it. A read followed by a write would let them.
	 */
	@Test
	void grantsExactlyTheLimitWhenAttemptsArriveTogether() throws Exception {
		int attempts = 8;
		CountDownLatch together = new CountDownLatch(1);
		List<Future<Optional<Duration>>> claims = new ArrayList<>();

		try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
			for (int attempt = 0; attempt < attempts; attempt++) {
				claims.add(pool.submit(() -> {
					together.await();
					return this.limiter.claim("ada@acme.test");
				}));
			}
			together.countDown();
		}

		long allowed = 0;
		for (Future<Optional<Duration>> claim : claims) {
			if (claim.get().isEmpty()) {
				allowed++;
			}
		}
		assertThat(allowed).isEqualTo(3);
	}

	/**
	 * Keys accumulate for as long as they are live, so something has to release the ones
	 * that are not. Without the sweep a stream of distinct addresses is a slow memory
	 * leak.
	 */
	@Test
	void releasesKeysThatCanNoLongerRefuseAnything() {
		for (int key = 0; key < 10_001; key++) {
			this.limiter.claim("address-" + key + "@acme.test");
		}
		assertThat(this.limiter.tracked()).isGreaterThan(10_000);

		this.clock.advanceBy(WINDOW.plusSeconds(1));
		this.limiter.claim("one-more@acme.test");

		// Everything from before the window has gone; only the attempt just made remains.
		assertThat(this.limiter.tracked()).isEqualTo(1);
	}

	/** Live keys survive a sweep, or the sweep would be handing out fresh allowances. */
	@Test
	void keepsKeysThatAreStillCounting() {
		this.limiter.claim("ada@acme.test");
		for (int key = 0; key < 10_001; key++) {
			this.limiter.claim("address-" + key + "@acme.test");
		}

		this.clock.advanceBy(Duration.ofMinutes(1));
		this.limiter.claim("trigger@acme.test");

		assertThat(this.limiter.tracked()).isGreaterThan(10_000);
	}

	/**
	 * Asking and counting are separate acts for a caller that counts failures: a sign-in
	 * cannot know it was a failure until the password has been checked, and asking must
	 * not itself spend a slot.
	 */
	@Test
	void answersWhetherAKeyIsSpentWithoutSpendingOne() {
		assertThat(this.limiter.refusalFor("ada@acme.test")).isEmpty();
		assertThat(this.limiter.refusalFor("ada@acme.test")).isEmpty();
		assertThat(this.limiter.refusalFor("ada@acme.test")).isEmpty();

		// None of that counted, so the whole allowance is still there.
		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.refusalFor("ada@acme.test")).isPresent();
	}

	@Test
	void countsWhatItIsToldToCount() {
		this.limiter.record("ada@acme.test");
		this.limiter.record("ada@acme.test");
		assertThat(this.limiter.refusalFor("ada@acme.test")).isEmpty();

		this.limiter.record("ada@acme.test");

		assertThat(this.limiter.refusalFor("ada@acme.test")).isPresent();
	}

	/**
	 * A refusal expires on its own schedule and cannot be extended by continuing to
	 * knock.
	 *
	 * <p>
	 * This is what keeps a per-account limit from becoming a way to lock somebody out of
	 * their own account. If attempts made while already refused were counted, anyone
	 * willing to keep sending could hold a stranger out for as long as they liked, and
	 * the harder they pushed the longer it would last — the limit would have become the
	 * attack it was added to prevent.
	 */
	@Test
	void cannotBeHeldOpenByCarryingOnKnocking() {
		spendByRecording(3);
		assertThat(this.limiter.refusalFor("ada@acme.test")).isPresent();

		// Somebody working through a password list, refused every time and continuing.
		for (int minute = 1; minute <= 14; minute++) {
			this.clock.advanceBy(Duration.ofMinutes(1));
			spendByRecording(5);
		}

		// One window after the attempts that caused it, and not a moment later.
		this.clock.advanceBy(Duration.ofMinutes(2));

		assertThat(this.limiter.refusalFor("ada@acme.test")).isEmpty();
	}

	/** Memory stays bounded even for a key nobody stops hitting. */
	@Test
	void remembersNoMoreThanTheLimitPerKey() {
		for (int attempt = 0; attempt < 500; attempt++) {
			this.limiter.record("ada@acme.test");
		}

		this.clock.advanceBy(WINDOW.plusSeconds(1));

		assertThat(this.limiter.refusalFor("ada@acme.test")).isEmpty();
	}

	/**
	 * Counting failures accumulates keys exactly as claiming does — a source address per
	 * attacker, an address per account guessed at — so it has to release them the same
	 * way.
	 */
	@Test
	void releasesKeysItWasToldToCountForToo() {
		for (int key = 0; key < 10_001; key++) {
			this.limiter.record("address-" + key + "@acme.test");
		}
		assertThat(this.limiter.tracked()).isGreaterThan(10_000);

		this.clock.advanceBy(WINDOW.plusSeconds(1));
		this.limiter.record("one-more@acme.test");

		assertThat(this.limiter.tracked()).isEqualTo(1);
	}

	@Test
	void forgetsOneKeyOnRequest() {
		spend(3);
		this.limiter.record("grace@acme.test");

		this.limiter.forget("ada@acme.test");

		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		// Somebody else's count is not theirs to clear.
		assertThat(this.limiter.tracked()).isEqualTo(2);
	}

	@Test
	void forgetsEverythingWhenCleared() {
		spend(3);

		this.limiter.clear();

		assertThat(this.limiter.claim("ada@acme.test")).isEmpty();
		assertThat(this.limiter.tracked()).isEqualTo(1);
	}

	private void spend(int attempts) {
		for (int attempt = 0; attempt < attempts; attempt++) {
			this.limiter.claim("ada@acme.test");
		}
	}

	private void spendByRecording(int attempts) {
		for (int attempt = 0; attempt < attempts; attempt++) {
			this.limiter.record("ada@acme.test");
		}
	}

	/** A clock a test can wind forward, so a window can pass without anything waiting. */
	private static final class MovableClock extends Clock {

		private Instant now;

		private MovableClock(Instant now) {
			this.now = now;
		}

		private void advanceBy(Duration elapsed) {
			this.now = this.now.plus(elapsed);
		}

		@Override
		public Instant instant() {
			return this.now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
