package eu.sonetas.aurevanta.ratelimit;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTests {

	@Test
	void keepsWhatIsConfigured() {
		RateLimitProperties properties = new RateLimitProperties(5, Duration.ofMinutes(30), 50, Duration.ofHours(1), 7,
				70, Duration.ofMinutes(5));

		assertThat(properties.perAddress()).isEqualTo(5);
		assertThat(properties.addressWindow()).isEqualTo(Duration.ofMinutes(30));
		assertThat(properties.perIp()).isEqualTo(50);
		assertThat(properties.ipWindow()).isEqualTo(Duration.ofHours(1));
		assertThat(properties.signInPerIp()).isEqualTo(7);
		assertThat(properties.signInPerAccount()).isEqualTo(70);
		assertThat(properties.signInWindow()).isEqualTo(Duration.ofMinutes(5));
	}

	/**
	 * Absent configuration binds to zero, and a limit of zero refuses everybody's first
	 * request — which would take the whole application down rather than leave it
	 * unprotected. Nothing configured is the ordinary case, so it has to mean the
	 * default.
	 */
	@Test
	void treatsAnAbsentLimitAsUnsetRatherThanAsALockout() {
		RateLimitProperties properties = new RateLimitProperties(0, null, 0, null, 0, 0, null);

		assertThat(properties.perAddress()).isPositive();
		assertThat(properties.perIp()).isPositive();
		assertThat(properties.addressWindow()).isEqualTo(Duration.ofMinutes(15));
		assertThat(properties.ipWindow()).isEqualTo(Duration.ofMinutes(15));
		assertThat(properties.signInPerIp()).isPositive();
		assertThat(properties.signInPerAccount()).isPositive();
		assertThat(properties.signInWindow()).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void treatsANegativeLimitTheSameWay() {
		RateLimitProperties properties = new RateLimitProperties(-1, null, -1, null, -1, -1, null);

		assertThat(properties.perAddress()).isPositive();
		assertThat(properties.perIp()).isPositive();
		assertThat(properties.signInPerIp()).isPositive();
		assertThat(properties.signInPerAccount()).isPositive();
	}

	/**
	 * The ratio is the design, not a coincidence. A per-account limit is also a way to
	 * lock somebody out of their own account, so it has to sit beyond what any one source
	 * can reach — otherwise a single machine turns a defence into an attack.
	 */
	@Test
	void putsTheSignInAccountLimitBeyondTheReachOfOneSource() {
		RateLimitProperties properties = new RateLimitProperties(0, null, 0, null, 0, 0, null);

		assertThat(properties.signInPerAccount()).isGreaterThan(properties.signInPerIp());
	}

	/** A source address stands for many people far more often than an inbox does. */
	@Test
	void isLooserPerSourceThanPerRecipient() {
		RateLimitProperties properties = new RateLimitProperties(0, null, 0, null, 0, 0, null);

		assertThat(properties.perIp()).isGreaterThan(properties.perAddress());
	}

}
