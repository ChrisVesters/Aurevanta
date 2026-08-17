package com.cvesters.aurevanta.forecast.model;

import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * <strong>{@code sharesOfASumAreTheShareOfItsVariance} is the oracle, and it is the whole
 * reason this milestone is checkable.</strong> Correlation against a scheduled plan has
 * no closed form — that is why `roadmap.md` says contribution has to be measured against
 * project completion rather than derived — but the degenerate case does: for independent
 * draws <em>summed</em>, one draw's squared correlation with the total is exactly its
 * share of the total variance, which is the number a summing model computes directly. So
 * the general method has to reduce to the simple formula in the one case the simple
 * formula is right for, and it does, to the last bit.
 *
 * <p>
 * The second one to read is {@code aPlanOfABillionHoursIsStillMeasuredCorrectly}, which
 * carries the measurement `m6-plan.md` opens with: the obvious one-pass formula returns
 * {@code NaN} there, and it is in the assertion so that nobody quietly puts it back.
 */
class ContributionsTests {

	private static final long SEED = 20260817L;

	/**
	 * Two independent sources, nine parts of the variance in the first and one in the
	 * second, over four runs that need no sampling at all: each has a mean of zero and
	 * they are exactly orthogonal, so the arithmetic is exact rather than converged.
	 */
	private static final double[] WIDE = { 3.0, -3.0, 0.0, 0.0 };

	private static final double[] NARROW = { 0.0, 0.0, 1.0, -1.0 };

	/**
	 * <strong>The oracle.</strong> A chain at capacity one with no common cause finishes
	 * when the sum of its draws says it does, and there the squared correlations are the
	 * variance shares and add to exactly 1 — 4.5 and 0.5 out of 5.0. Anything that
	 * changes this has stopped computing what a summing model computed, which is the
	 * thing M6 is supposed to generalise rather than replace.
	 */
	@Test
	void sharesOfASumAreTheShareOfItsVariance() {
		Contributions measured = summed(WIDE, NARROW);

		assertThat(measured.of(0).shareOfSpread()).isCloseTo(0.9, within(1e-12));
		assertThat(measured.of(1).shareOfSpread()).isCloseTo(0.1, within(1e-12));
		assertThat(measured.of(0).shareOfSpread() + measured.of(1).shareOfSpread()).isCloseTo(1.0, within(1e-12));
		// And the correlations themselves are the closed form: sd_i over the total's sd.
		assertThat(measured.of(0).correlation()).isCloseTo(Math.sqrt(4.5) / Math.sqrt(5.0), within(1e-12));
		assertThat(measured.of(1).correlation()).isCloseTo(Math.sqrt(0.5) / Math.sqrt(5.0), within(1e-12));
	}

	/**
	 * The same oracle on drawn numbers rather than constructed ones, because a formula
	 * that only works on four orthogonal points is not a formula. Ten independent normal
	 * sources of differing width, summed: each one's correlation with the total is
	 * {@code sd_i / sqrt(sum of sd_j squared)}, and the sampling error at a hundred
	 * thousand runs is about a thousandth.
	 */
	@Test
	void theClosedFormHoldsForRealDraws() {
		double[] widths = { 1.0, 2.0, 3.0, 5.0, 8.0, 13.0, 0.5, 4.0, 6.0, 2.5 };
		double total = 0.0;
		for (double width : widths) {
			total += width * width;
		}
		RandomGenerator random = new Random(SEED);
		Contributions measured = new Contributions(widths.length);
		double[] drawn = new double[widths.length];

		for (int run = 0; run < 100_000; run++) {
			double sum = 0.0;
			for (int at = 0; at < widths.length; at++) {
				drawn[at] = 100.0 + widths[at] * random.nextGaussian();
				sum += drawn[at];
			}
			measured.observed(drawn, sum);
		}

		for (int at = 0; at < widths.length; at++) {
			assertThat(measured.of(at).correlation()).as("source %d", at)
				.isCloseTo(widths[at] / Math.sqrt(total), within(0.01));
		}
	}

	/**
	 * <strong>The measurement `m6-plan.md` opens with, as a regression.</strong> A
	 * correlation does not care where zero is, so shifting both series by a billion must
	 * change nothing — and it changes nothing here, while the sums-of-squares formula
	 * subtracts two nearly equal enormous numbers and comes back with {@code NaN}. That
	 * is not merely a worse answer: {@code NaN} is not valid JSON, so the endpoint that
	 * published it would fail rather than lie, which is the better of two bad outcomes
	 * and is not a defence.
	 */
	@Test
	void aPlanOfABillionHoursIsStillMeasuredCorrectly() {
		double base = 1e9;
		double[] shiftedWide = shifted(WIDE, base);
		double[] shiftedSum = shifted(sum(WIDE, NARROW), base);

		Contributions measured = new Contributions(1);
		for (int run = 0; run < WIDE.length; run++) {
			measured.observed(new double[] { shiftedWide[run] }, shiftedSum[run]);
		}

		assertThat(measured.of(0).correlation()).isCloseTo(Math.sqrt(0.9), within(1e-12));
		// And what the obvious formula answers on the same numbers, so that putting it
		// back
		// is a failing test rather than a tidier-looking method.
		assertThat(naiveCorrelation(shiftedWide, shiftedSum)).isNaN();
	}

	/**
	 * A correlation is a shape, not a size: sixty times the hours is the same ranking.
	 */
	@Test
	void changingTheUnitChangesNoRanking() {
		Contributions hours = summed(WIDE, NARROW);
		Contributions minutes = summed(scaled(WIDE, 60.0), scaled(NARROW, 60.0));

		assertThat(minutes.of(0).correlation()).isCloseTo(hours.of(0).correlation(), within(1e-12));
		assertThat(minutes.of(1).correlation()).isCloseTo(hours.of(1).correlation(), within(1e-12));
	}

	/**
	 * <strong>Decision 5, and it is the ordinary case rather than an edge one.</strong>
	 * An item nobody estimated weighs nothing in every run; work already finished has
	 * nothing left; and three identical numbers are somebody saying they are certain,
	 * which M2 accepts on purpose. All three never move, so none of them is what the
	 * finish moved with — and none of them is a {@code NaN} that would sort unpredictably
	 * through a ranking and fail to serialise.
	 */
	@Test
	void aSourceThatNeverVariesContributesExactlyNothing() {
		Contributions measured = new Contributions(3);
		double[] outcome = { 10.0, 14.0, 9.0, 30.0 };

		for (int run = 0; run < outcome.length; run++) {
			// Nobody estimated it; it is finished; somebody is certain it is four hours.
			measured.observed(new double[] { 0.0, 0.0, 4.0 }, outcome[run]);
		}

		assertThat(measured.of(0)).isEqualTo(Contribution.NONE);
		assertThat(measured.of(1).correlation()).isEqualTo(0.0);
		assertThat(measured.of(2).shareOfSpread()).isEqualTo(0.0);
	}

	/**
	 * The other half of the same guard: a plan that finishes at the same moment in every
	 * run has no spread to attribute, so nothing is attributed any of it — however much
	 * the things inside it moved.
	 */
	@Test
	void aPlanWithNoSpreadAttributesNoneOfIt() {
		Contributions measured = new Contributions(1);

		for (double drawn : WIDE) {
			measured.observed(new double[] { drawn }, 40.0);
		}

		assertThat(measured.of(0)).isEqualTo(Contribution.NONE);
	}

	/** The one source that decides everything gets all of it. */
	@Test
	void aSourceTheFinishFollowsExactlyTakesTheWholeSpread() {
		Contributions measured = new Contributions(1);

		for (double drawn : WIDE) {
			measured.observed(new double[] { drawn }, 12.0 + 3.0 * drawn);
		}

		assertThat(measured.of(0).correlation()).isCloseTo(1.0, within(1e-12));
		assertThat(measured.of(0).shareOfSpread()).isCloseTo(1.0, within(1e-12));
	}

	/**
	 * A scheduling anomaly is not a mistake — a shorter task can free a slot early and
	 * change what gets picked up next — so a negative correlation has to survive the
	 * accumulation rather than be clamped away. Squaring it is what makes the ranking
	 * read the same either way.
	 */
	@Test
	void aSourceTheFinishRunsAgainstIsNegativeAndStillRanks() {
		Contributions measured = new Contributions(1);

		for (double drawn : WIDE) {
			measured.observed(new double[] { drawn }, 12.0 - 3.0 * drawn);
		}

		assertThat(measured.of(0).correlation()).isCloseTo(-1.0, within(1e-12));
		assertThat(measured.of(0).shareOfSpread()).isCloseTo(1.0, within(1e-12));
	}

	@Test
	void anAccumulatorThatHasSeenNothingSaysSo() {
		Contributions measured = new Contributions(2);

		assertThat(measured.runs()).isZero();
		assertThat(measured.of(0)).isEqualTo(Contribution.NONE);
	}

	@Test
	void countsTheRunsItWasShown() {
		Contributions measured = summed(WIDE, NARROW);

		assertThat(measured.runs()).isEqualTo(WIDE.length);
	}

	@Test
	void refusesARunThatDoesNotAnswerForEverySource() {
		Contributions measured = new Contributions(2);

		assertThatIllegalArgumentException().isThrownBy(() -> measured.observed(new double[] { 1.0 }, 5.0));
	}

	@Test
	void refusesAForecastWithFewerSourcesThanNone() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Contributions(-1));
	}

	/** Two sources whose sum is the outcome, which is a chain at capacity one. */
	private static Contributions summed(double[] first, double[] second) {
		Contributions measured = new Contributions(2);
		double[] outcome = sum(first, second);
		for (int run = 0; run < outcome.length; run++) {
			measured.observed(new double[] { first[run], second[run] }, outcome[run]);
		}
		return measured;
	}

	private static double[] sum(double[] first, double[] second) {
		double[] total = new double[first.length];
		for (int at = 0; at < total.length; at++) {
			total[at] = first[at] + second[at];
		}
		return total;
	}

	private static double[] shifted(double[] values, double by) {
		double[] moved = new double[values.length];
		for (int at = 0; at < values.length; at++) {
			moved[at] = values[at] + by;
		}
		return moved;
	}

	private static double[] scaled(double[] values, double by) {
		double[] stretched = new double[values.length];
		for (int at = 0; at < values.length; at++) {
			stretched[at] = values[at] * by;
		}
		return stretched;
	}

	/**
	 * The formula this class deliberately does not use, kept here so that its answer is
	 * asserted rather than described.
	 */
	private static double naiveCorrelation(double[] values, double[] outcome) {
		int runs = values.length;
		double sx = 0.0;
		double sy = 0.0;
		double sxx = 0.0;
		double syy = 0.0;
		double sxy = 0.0;
		for (int at = 0; at < runs; at++) {
			sx += values[at];
			sy += outcome[at];
			sxx += values[at] * values[at];
			syy += outcome[at] * outcome[at];
			sxy += values[at] * outcome[at];
		}
		return (runs * sxy - sx * sy) / (Math.sqrt(runs * sxx - sx * sx) * Math.sqrt(runs * syy - sy * sy));
	}

}
