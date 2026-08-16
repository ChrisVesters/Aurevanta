package com.cvesters.aurevanta.forecast.model;

import java.util.Arrays;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * <strong>{@code aFactorOfNoneTakesNoDrawAtAll} is the one to read.</strong> Everything
 * else here checks arithmetic; that one checks that a factor which changes no number also
 * costs no randomness, which is what keeps a version 1 run replayable by a version 2
 * engine. It is invisible in review and nothing else in the suite would notice it
 * breaking.
 */
class TeamFactorTests {

	private static final long SEED = 20260816L;

	/**
	 * Enough draws that the median of them is worth a tenth of a percent, which is what
	 * separates a factor centred on 1 from one centred on its own mean.
	 */
	private static final int MEASURED = 200_000;

	@Test
	void theStretchItWasAskedForIsTheStretchItPutsAtTheNinetieth() {
		TeamFactor factor = TeamFactor.from(30.0);

		assertThat(factor.multiplier().at(Normal.P90_Z)).isCloseTo(1.30, within(1e-12));
	}

	/**
	 * The other end, which is not asked for and follows from the middle being pinned: a
	 * stretch is as good as it is bad, in ratio. That is what makes one number enough.
	 */
	@Test
	void aGoodStretchIsExactlyAsGoodAsABadOneIsBad() {
		TeamFactor factor = TeamFactor.from(30.0);

		assertThat(factor.multiplier().at(-Normal.P90_Z)).isCloseTo(1.0 / 1.30, within(1e-12));
	}

	/**
	 * <strong>Exactly one, not nearly one.</strong> A factor whose middle drifts is a
	 * factor that moves the centre of every forecast while claiming only to widen it, and
	 * the drift would be far too small for any band to show.
	 */
	@Test
	void itsMiddleIsExactlyOneHoweverBadTheStretch() {
		assertThat(TeamFactor.from(5.0).multiplier().median()).isEqualTo(1.0);
		assertThat(TeamFactor.from(30.0).multiplier().median()).isEqualTo(1.0);
		assertThat(TeamFactor.from(400.0).multiplier().median()).isEqualTo(1.0);
	}

	/**
	 * The asymmetry that makes a multiplier a multiplier: half the runs are worse than
	 * ordinary and half are better, and yet the average run is worse than ordinary —
	 * because a stretch that doubles the work is further from 1 than one that halves it.
	 */
	@Test
	void halfOfManyDrawsFallEitherSideOfOneAndTheirAverageSitsAbove() {
		TeamFactor factor = TeamFactor.from(30.0);
		RandomGenerator random = new Random(SEED);
		double[] drawn = new double[MEASURED];
		double total = 0.0;

		for (int at = 0; at < MEASURED; at++) {
			drawn[at] = factor.sample(random);
			total += drawn[at];
		}
		Arrays.sort(drawn);

		assertThat(drawn[MEASURED / 2]).isCloseTo(1.0, withinPercentage(0.5));
		assertThat(total / MEASURED).isGreaterThan(1.0).isCloseTo(factor.multiplier().mean(), withinPercentage(0.5));
	}

	@Test
	void noStretchAtAllMultipliesByExactlyOne() {
		TeamFactor none = TeamFactor.from(0.0);

		assertThat(none).isEqualTo(TeamFactor.NONE);
		assertThat(none.sample(new Random(SEED))).isEqualTo(1.0);
	}

	/**
	 * <strong>The subtlest thing in this milestone, asserted rather than
	 * intended.</strong> A factor of none must not consume the generator, or every draw
	 * after it shifts and a seed stops meaning what it meant — which would silently
	 * unreplay every forecast stored before there was a factor at all, with no number
	 * changing to say so.
	 */
	@Test
	void aFactorOfNoneTakesNoDrawAtAll() {
		RandomGenerator untouched = new Random(SEED);

		for (int at = 0; at < 100; at++) {
			assertThat(TeamFactor.NONE.sample(untouched)).isEqualTo(1.0);
		}

		assertThat(untouched.nextGaussian()).isEqualTo(new Random(SEED).nextGaussian());
	}

	/**
	 * The mirror of the above, so that it cannot pass because {@code sample} does nothing
	 * at all: a factor that stretches does spend a draw.
	 */
	@Test
	void aFactorThatStretchesDoesTakeOne() {
		RandomGenerator spent = new Random(SEED);

		TeamFactor.from(30.0).sample(spent);

		assertThat(spent.nextGaussian()).isNotEqualTo(new Random(SEED).nextGaussian());
	}

	@Test
	void theSameSeedStretchesTheSameWay() {
		TeamFactor factor = TeamFactor.from(45.0);

		assertThat(factor.sample(new Random(SEED))).isEqualTo(factor.sample(new Random(SEED)));
	}

	@Test
	void refusesAStretchThatIsAnImprovement() {
		assertThatIllegalArgumentException().isThrownBy(() -> TeamFactor.from(-1.0));
		assertThatIllegalArgumentException().isThrownBy(() -> TeamFactor.from(Double.NaN));
	}

	/**
	 * The pin, refused loudly at the door rather than trusted. A multiplier with a
	 * {@code mu} of anything but zero has a median that is not 1, which is precisely the
	 * mistake nothing downstream would report.
	 */
	@Test
	void refusesAMultiplierWhoseMiddleIsNotOne() {
		LogNormalFit offCentre = new LogNormalFit(0.5, 0.2);

		assertThatIllegalArgumentException().isThrownBy(() -> new TeamFactor(offCentre));
		assertThatNullPointerException().isThrownBy(() -> new TeamFactor(null));
	}

}
