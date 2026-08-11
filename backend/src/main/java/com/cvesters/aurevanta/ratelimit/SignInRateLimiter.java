package com.cvesters.aurevanta.ratelimit;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import com.cvesters.aurevanta.problem.TooManyRequestsException;

/**
 * Bounds how fast passwords can be guessed.
 *
 * <p>
 * Sign-in is unauthenticated by definition, so without this the only thing standing
 * between an attacker and an account is how long bcrypt takes — which is a cost measured
 * per attempt, not a limit on how many attempts there are.
 *
 * <p>
 * <strong>Failures are counted, successes are not.</strong> Somebody signing in correctly
 * from three devices has done nothing that needs slowing down, and counting it would
 * throttle exactly the people this is meant to protect.
 */
public class SignInRateLimiter {

	private final RateLimiter bySource;

	private final RateLimiter byAccount;

	SignInRateLimiter(RateLimiter bySource, RateLimiter byAccount) {
		this.bySource = bySource;
		this.byAccount = byAccount;
	}

	/**
	 * Refuses before the password is checked, so an exhausted caller costs no bcrypt at
	 * all.
	 *
	 * <p>
	 * The account is keyed by the address as submitted, whether or not it belongs to
	 * anyone. An unknown address that is guessed at ten times is refused exactly as a
	 * real one would be — anything else and the refusal itself would answer which
	 * addresses have accounts, which is the question {@code invalid_credentials} exists
	 * to leave unanswered.
	 * @throws TooManyRequestsException if either budget is spent
	 */
	public void refuseIfExhausted(String sourceAddress, String emailAddress) {
		refuse(this.bySource.refusalFor(sourceAddress));
		refuse(this.byAccount.refusalFor(normalise(emailAddress)));
	}

	/** Counts one wrong guess. */
	public void recordFailure(String sourceAddress, String emailAddress) {
		this.bySource.record(sourceAddress);
		this.byAccount.record(normalise(emailAddress));
	}

	/**
	 * Clears what has accumulated against an account that has just been signed into
	 * successfully.
	 *
	 * <p>
	 * The source keeps its count: whoever was guessing is still guessing, and one correct
	 * password among their attempts is no reason to hand them a fresh allowance.
	 */
	public void succeeded(String emailAddress) {
		this.byAccount.forget(normalise(emailAddress));
	}

	private static void refuse(Optional<Duration> refusal) {
		refusal.ifPresent((retryAfter) -> {
			throw new TooManyRequestsException(retryAfter);
		});
	}

	private static String normalise(String emailAddress) {
		return emailAddress.strip().toLowerCase(Locale.ROOT);
	}

	/** Forgets every count, for tests that share an application context. */
	public void clear() {
		this.bySource.clear();
		this.byAccount.clear();
	}

}
