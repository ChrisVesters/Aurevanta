package com.cvesters.aurevanta.forecast.model;

import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * <strong>{@code aPlanTooSmallToGrowGrowsAnyway} is the one to read.</strong> Ten items
 * growing 4% is four tenths of an item, and rounding that to the nearest whole number
 * gives none — not usually, but in every run for ever, so a small and entirely real
 * growth becomes exactly no growth. It is the sort of bug that produces a plausible
 * number, which is the failure mode this whole milestone is arranged against.
 */
class ScopeGrowthTests {

	private static final long SEED = 20260816L;

	private static final int MEASURED = 200_000;

	@Test
	void theRangeItWasAskedForIsTheRangeItGrowsBy() {
		ScopeGrowth growth = ScopeGrowth.from(40.0, 90.0);

		assertThat(growth.multiplier().at(-Normal.P90_Z)).isCloseTo(1.40, within(1e-12));
		assertThat(growth.multiplier().at(Normal.P90_Z)).isCloseTo(1.90, within(1e-12));
	}

	/**
	 * A plan somebody is certain about grows by exactly that much, and the rounding has
	 * nothing left to decide.
	 */
	@Test
	void aCertainGrowthIsTheSameCountEveryTime() {
		ScopeGrowth fifth = ScopeGrowth.from(20.0, 20.0);
		RandomGenerator random = new Random(SEED);

		for (int at = 0; at < 100; at++) {
			assertThat(fifth.sample(10, random)).isEqualTo(2);
		}
	}

	/**
	 * <strong>The test that fails if the rounding is naive.</strong> Four tenths of an
	 * item cannot be generated, so it is generated four times in ten — and the average
	 * over many runs is the number that was sampled, which is the only thing that makes
	 * sampling a distribution mean anything. Rounding to nearest gives a mean of zero
	 * here and passes every other test in this file.
	 */
	@Test
	void aPlanTooSmallToGrowGrowsAnyway() {
		ScopeGrowth slight = ScopeGrowth.from(4.0, 4.0);
		RandomGenerator random = new Random(SEED);
		int total = 0;
		int grew = 0;

		for (int at = 0; at < MEASURED; at++) {
			int found = slight.sample(10, random);
			assertThat(found).isBetween(0, 1);
			total += found;
			grew += (found > 0) ? 1 : 0;
		}

		assertThat(total / (double) MEASURED).isCloseTo(0.4, withinPercentage(2.0));
		assertThat(grew).isGreaterThan(0).isLessThan(MEASURED);
	}

	/** And the same holds where the count is a distribution rather than a fraction. */
	@Test
	void theMeanCountIsTheOneThatWasSampled() {
		ScopeGrowth growth = ScopeGrowth.from(20.0, 60.0);
		RandomGenerator random = new Random(SEED);
		int total = 0;

		for (int at = 0; at < MEASURED; at++) {
			total += growth.sample(10, random);
		}

		assertThat(total / (double) MEASURED).isCloseTo(10.0 * (growth.multiplier().mean() - 1.0),
				withinPercentage(1.0));
	}

	/**
	 * <strong>A low end of nothing is one run in ten with no growth at all</strong>, by
	 * construction rather than by accident: the tenth percentile of the multiplier is
	 * exactly 1, so a tenth of the draws fall below it, and this model has no way to say
	 * a plan shrank. That is the price of fitting the multiplier rather than the
	 * percentage, and it is what makes "usually nothing, sometimes half as much again" an
	 * answer somebody can give.
	 */
	@Test
	void aLowEndOfNothingMeansSomeRunsFindNothing() {
		ScopeGrowth growth = ScopeGrowth.from(0.0, 40.0);
		RandomGenerator random = new Random(SEED);
		int nothing = 0;
		int total = 0;

		for (int at = 0; at < MEASURED; at++) {
			int found = growth.sample(10, random);
			nothing += (found == 0) ? 1 : 0;
			total += found;
		}

		assertThat(nothing / (double) MEASURED).isGreaterThan(0.10);
		// Clamped rather than negative, so the average sits a little above the fit's own.
		assertThat(total / (double) MEASURED).isGreaterThan(10.0 * (growth.multiplier().mean() - 1.0));
	}

	@Test
	void noGrowthAtAllIsNoItemsAtAll() {
		assertThat(ScopeGrowth.from(0.0, 0.0)).isEqualTo(ScopeGrowth.NONE);
		assertThat(ScopeGrowth.NONE.sample(500, new Random(SEED))).isZero();
	}

	/**
	 * <strong>The same subtlety {@link TeamFactor#sample} has, and the same
	 * reason.</strong> A parameter that generates no work must also cost no randomness,
	 * or every draw after it shifts and a run stored before there was scope growth stops
	 * replaying.
	 */
	@Test
	void aPlanExpectedToGrowByNothingTakesNoDrawAtAll() {
		RandomGenerator untouched = new Random(SEED);

		for (int at = 0; at < 100; at++) {
			assertThat(ScopeGrowth.NONE.sample(10, untouched)).isZero();
		}

		assertThat(untouched.nextGaussian()).isEqualTo(new Random(SEED).nextGaussian());
	}

	/** The mirror, so the above is not passing because nothing draws anything. */
	@Test
	void aPlanExpectedToGrowDoesTakeDraws() {
		RandomGenerator spent = new Random(SEED);

		ScopeGrowth.from(20.0, 60.0).sample(10, spent);

		assertThat(spent.nextGaussian()).isNotEqualTo(new Random(SEED).nextGaussian());
	}

	@Test
	void theSameSeedFindsTheSameWork() {
		ScopeGrowth growth = ScopeGrowth.from(20.0, 60.0);

		assertThat(growth.sample(40, new Random(SEED))).isEqualTo(growth.sample(40, new Random(SEED)));
	}

	@Test
	void refusesARangeThatCannotBeOne() {
		assertThatIllegalArgumentException().isThrownBy(() -> ScopeGrowth.from(-1.0, 40.0));
		assertThatIllegalArgumentException().isThrownBy(() -> ScopeGrowth.from(Double.NaN, 40.0));
		assertThatIllegalArgumentException().isThrownBy(() -> ScopeGrowth.from(90.0, 40.0));
		assertThatNullPointerException().isThrownBy(() -> new ScopeGrowth(null));
	}

}
