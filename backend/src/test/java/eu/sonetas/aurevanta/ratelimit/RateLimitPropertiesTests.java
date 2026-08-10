package eu.sonetas.aurevanta.ratelimit;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTests {

	@Test
	void keepsWhatIsConfigured() {
		RateLimitProperties properties = new RateLimitProperties(5, Duration.ofMinutes(30), 50, Duration.ofHours(1));

		assertThat(properties.perAddress()).isEqualTo(5);
		assertThat(properties.addressWindow()).isEqualTo(Duration.ofMinutes(30));
		assertThat(properties.perIp()).isEqualTo(50);
		assertThat(properties.ipWindow()).isEqualTo(Duration.ofHours(1));
	}

	/**
	 * Absent configuration binds to zero, and a limit of zero refuses everybody's first
	 * request — which would take the whole application down rather than leave it
	 * unprotected. Nothing configured is the ordinary case, so it has to mean the
	 * default.
	 */
	@Test
	void treatsAnAbsentLimitAsUnsetRatherThanAsALockout() {
		RateLimitProperties properties = new RateLimitProperties(0, null, 0, null);

		assertThat(properties.perAddress()).isPositive();
		assertThat(properties.perIp()).isPositive();
		assertThat(properties.addressWindow()).isEqualTo(Duration.ofMinutes(15));
		assertThat(properties.ipWindow()).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void treatsANegativeLimitTheSameWay() {
		RateLimitProperties properties = new RateLimitProperties(-1, null, -1, null);

		assertThat(properties.perAddress()).isPositive();
		assertThat(properties.perIp()).isPositive();
	}

	/** A source address stands for many people far more often than an inbox does. */
	@Test
	void isLooserPerSourceThanPerRecipient() {
		RateLimitProperties properties = new RateLimitProperties(0, null, 0, null);

		assertThat(properties.perIp()).isGreaterThan(properties.perAddress());
	}

}
