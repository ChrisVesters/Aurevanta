package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.item.WorkItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * One draw of how much work an item still has left, and the two decisions that make it an
 * honest one.
 *
 * <p>
 * <strong>Two of these assert something that will be reported as a bug</strong>, and they
 * are the ones to read first: {@code twoEstimatorsWidenTheBandRatherThanAveragingIt}, and
 * {@code workThatHasRunLongEnoughHasMoreLeftRatherThanLess}. Disagreement between
 * colleagues is supposed to widen the band, and a task that has already overrun is
 * supposed to have <em>more</em> ahead of it than one that has not. Neither is an
 * accident, so both are pinned here rather than left to be argued about later.
 *
 * <p>
 * <strong>The one that would catch a subtly wrong sampler is
 * {@code aConditionalDrawMatchesTheConditionalMean}</strong>, which is the only test here
 * with an answer worked out rather than sampled.
 *
 * <p>
 * Everything is drawn from a seeded generator, so every number below is reproducible.
 * Tolerances are sampling error rather than taste: at two hundred thousand draws the
 * standard error of a mean is a few parts in ten thousand, and the assertions are set
 * several times that so the suite does not fail on arithmetic that is working.
 */
class ItemModelTests {

	private static final long SEED = 20260814L;

	private static final int DRAWS = 200_000;

	/**
	 * Moderately wide, of the shape a real estimate has: a factor of five between ends.
	 */
	private static final LogNormalFit ORDINARY = LogNormalFit.from(8.0, 40.0);

	/** Narrow enough that a large overrun leaves it no probability at all. */
	private static final LogNormalFit CONFIDENT = LogNormalFit.from(19.0, 21.0);

	@Test
	void finishedWorkDrawsNothingHoweverItWasEstimated() {
		assertThat(only(item(WorkItemStatus.DONE, 0.0, ORDINARY))).isZero();
		assertThat(only(item(WorkItemStatus.DONE, 12.0, ORDINARY))).isZero();
	}

	/**
	 * The difference between having no estimate and having no place in the plan. Its
	 * effort is unknown, so its effort is zero — but it is still an item, and step 3
	 * keeps it in the graph so the work either side of it stays in order.
	 */
	@Test
	void unestimatedWorkDrawsNothingAndIsStillAnItem() {
		ItemModel unestimated = new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.NOT_STARTED, 0.0);

		assertThat(only(unestimated)).isZero();
		assertThat(unestimated.estimates()).isEmpty();
	}

	@Test
	void unestimatedWorkUnderWayDrawsNothingEither() {
		assertThat(only(new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.IN_PROGRESS, 6.0))).isZero();
	}

	/**
	 * <strong>The oracle, arriving two steps early.</strong> A log-normal's mean and
	 * variance are exact functions of its two parameters, so a sampler that is right
	 * converges on them and one that is subtly wrong does not. Step 4 builds the
	 * whole-plan version of this out of the same arithmetic; this is the one-item case,
	 * where nothing else can be blamed.
	 */
	@Test
	void oneEstimatorsDrawsConvergeOnTheAnalyticMoments() {
		double[] drawn = draws(item(WorkItemStatus.NOT_STARTED, 0.0, ORDINARY));

		assertThat(mean(drawn)).isCloseTo(ORDINARY.mean(), withinPercentage(1.0));
		assertThat(variance(drawn)).isCloseTo(ORDINARY.variance(), withinPercentage(5.0));
	}

	/** The same, for somebody who was certain: every draw is the number they gave. */
	@Test
	void anEstimatorWhoWasCertainDrawsTheSameNumberEveryTime() {
		LogNormalFit certain = LogNormalFit.from(12.0, 12.0);
		double[] drawn = draws(item(WorkItemStatus.NOT_STARTED, 0.0, certain));

		assertThat(least(drawn)).isEqualTo(12.0);
		assertThat(most(drawn)).isEqualTo(12.0);
	}

	/**
	 * <strong>Decision 3, asserted rather than described.</strong> One colleague says
	 * five to ten hours and another says fifty to a hundred; the mixture sits between
	 * them, and its spread is far larger than either of theirs, because the distance
	 * between two people who disagree is uncertainty that neither of them stated on their
	 * own.
	 *
	 * <p>
	 * Averaging their parameters would have produced a band about as tight as each of
	 * theirs, centred on a number neither of them said. That is the version of this that
	 * will get proposed the first time somebody sees the wide band, and it is the one
	 * thing here that turns uncertainty into confidence.
	 */
	@Test
	void twoEstimatorsWidenTheBandRatherThanAveragingIt() {
		LogNormalFit optimist = LogNormalFit.from(5.0, 10.0);
		LogNormalFit pessimist = LogNormalFit.from(50.0, 100.0);
		double[] drawn = draws(item(WorkItemStatus.NOT_STARTED, 0.0, optimist, pessimist));

		assertThat(mean(drawn)).isBetween(optimist.mean(), pessimist.mean());
		assertThat(variance(drawn)).isGreaterThan(optimist.variance()).isGreaterThan(pessimist.variance());
		// Not marginally greater: the between-estimator distance is the larger part of
		// it.
		assertThat(variance(drawn)).isGreaterThan(2.0 * pessimist.variance());
	}

	/** Each estimator gets picked about as often as the other, over enough draws. */
	@Test
	void neitherEstimatorIsPreferred() {
		LogNormalFit optimist = LogNormalFit.from(5.0, 10.0);
		LogNormalFit pessimist = LogNormalFit.from(50.0, 100.0);
		double[] drawn = draws(item(WorkItemStatus.NOT_STARTED, 0.0, optimist, pessimist));

		long low = 0;
		for (double value : drawn) {
			if (value < 25.0) {
				low++;
			}
		}
		assertThat((double) low / drawn.length).isCloseTo(0.5, withinPercentage(2.0));
	}

	@Test
	void workUnderWayNeverDrawsLessThanNothing() {
		double[] drawn = draws(item(WorkItemStatus.IN_PROGRESS, 20.0, ORDINARY));

		assertThat(least(drawn)).isGreaterThanOrEqualTo(0.0);
		assertThat(most(drawn)).isFinite();
	}

	/**
	 * The remainder is what is left <em>beyond</em> what has been spent, so the total
	 * this item will have cost is always more than has gone into it already. That is the
	 * whole of what conditioning means, and it is why this is not "the estimate minus the
	 * hours".
	 */
	@Test
	void workUnderWayAlwaysHasSomethingLeft() {
		double[] drawn = draws(item(WorkItemStatus.IN_PROGRESS, 20.0, ORDINARY));

		assertThat(least(drawn)).isPositive();
	}

	/**
	 * <strong>The oracle for the conditional draw, and the strongest test in this
	 * file.</strong> The mean of a log-normal, given that it has already exceeded some
	 * amount, has a closed form:
	 *
	 * <pre>
	 * E[X | X &gt; a] = mean · Φ((mu + sigma² − ln a)/sigma) / Φ((mu − ln a)/sigma)
	 * </pre>
	 *
	 * <p>
	 * The sampler reaches its answer by inverting the surviving tail and averaging two
	 * hundred thousand draws. This reaches the same answer by evaluating
	 * {@link Normal#cdf} at two points and doing no sampling at all. Neither path depends
	 * on the other being right, so agreement to a part in ten thousand says the
	 * conditioning of decision 5 is arithmetic rather than intention.
	 */
	@Test
	void aConditionalDrawMatchesTheConditionalMean() {
		for (double spent : new double[] { 5.0, 20.0, 50.0, 200.0 }) {
			double[] drawn = draws(item(WorkItemStatus.IN_PROGRESS, spent, ORDINARY));

			assertThat(mean(drawn)).isCloseTo(meanRemainingAfter(ORDINARY, spent), withinPercentage(1.0));
		}
	}

	/**
	 * <strong>The property that looks like a bug</strong>, and it is worth being exact
	 * about what it claims. Four times the effort has gone into the second item and it
	 * has <em>more</em> ahead of it, not less, because a right-skewed distribution says
	 * work that has already run long is more likely to be work that runs very long.
	 *
	 * <p>
	 * It is not monotonic, and saying so would overstate it: the remainder falls first,
	 * while the bulk of the distribution is still being used up, and rises once the tail
	 * is all that is left — 17.2 hours at five spent, 14.2 at twenty, 17.1 at fifty, 34.0
	 * at two hundred. Fifty against two hundred is well inside the rising part, which is
	 * the part anybody managing a late task is in.
	 */
	@Test
	void workThatHasRunLongEnoughHasMoreLeftRatherThanLess() {
		double late = mean(draws(item(WorkItemStatus.IN_PROGRESS, 50.0, ORDINARY)));
		double later = mean(draws(item(WorkItemStatus.IN_PROGRESS, 200.0, ORDINARY)));

		assertThat(later).isGreaterThan(late);
		// A model that subtracted spent hours from the estimate would have run out here.
		assertThat(later).isGreaterThan(ORDINARY.mean());
	}

	/**
	 * Past its own P90 and still answering — with a remainder that is positive and
	 * finite, because the distribution has no upper bound to have been used up.
	 */
	@Test
	void workThatHasOutrunItsOwnWorstCaseStillHasAnAnswer() {
		double[] drawn = draws(item(WorkItemStatus.IN_PROGRESS, 50.0, ORDINARY));

		assertThat(least(drawn)).isPositive();
		assertThat(most(drawn)).isFinite();
		assertThat(mean(drawn)).isPositive().isFinite();
	}

	/**
	 * <strong>An estimate can be outrun so comprehensively that there is nothing left to
	 * condition on.</strong> Nineteen to twenty-one hours, and a hundred spent: the
	 * distribution holds no probability out there at all, so the remainder collapses to
	 * nothing. That is not a forecast, it is the model reporting that this estimate has
	 * been falsified — and what fixes it is a revision, which M2 makes a new row rather
	 * than a rewrite.
	 */
	@Test
	void anEstimateComprehensivelyOutrunHasNothingLeftToSay() {
		assertThat(only(item(WorkItemStatus.IN_PROGRESS, 100.0, CONFIDENT))).isZero();
	}

	/** Certainty, conditioned: what is left is arithmetic, and nothing is drawn. */
	@Test
	void certaintyUnderWayIsSimplyWhatIsLeftOfIt() {
		LogNormalFit certain = LogNormalFit.from(12.0, 12.0);

		assertThat(only(item(WorkItemStatus.IN_PROGRESS, 5.0, certain))).isCloseTo(7.0, withinPercentage(1e-9));
		assertThat(only(item(WorkItemStatus.IN_PROGRESS, 30.0, certain))).isZero();
	}

	/**
	 * Barely started, against an estimate narrow enough that the surviving mass rounds to
	 * one — the clamp that keeps {@code quantile} defined, and which no answer here
	 * should be able to notice.
	 */
	@Test
	void workBarelyBegunIsStillAlmostEntirelyAhead() {
		double[] drawn = draws(item(WorkItemStatus.IN_PROGRESS, 0.01, CONFIDENT));

		assertThat(most(drawn)).isFinite();
		assertThat(mean(drawn)).isCloseTo(CONFIDENT.mean() - 0.01, withinPercentage(1.0));
	}

	@Test
	void theSameSeedDrawsTheSameSequence() {
		ItemModel item = item(WorkItemStatus.IN_PROGRESS, 20.0, ORDINARY, CONFIDENT);

		assertThat(draws(item)).containsExactly(draws(item));
	}

	@Test
	void adifferentSeedDrawsADifferentSequence() {
		ItemModel item = item(WorkItemStatus.NOT_STARTED, 0.0, ORDINARY);

		assertThat(draws(item, SEED)).isNotEqualTo(draws(item, SEED + 1));
	}

	/**
	 * The estimates are copied on the way in. A run has to be reproducible from what it
	 * was given, and a list its caller can still add to is not an input.
	 */
	@Test
	void keepsItsOwnCopyOfTheEstimatesItWasGiven() {
		List<LogNormalFit> mutable = new ArrayList<>(List.of(ORDINARY));
		ItemModel item = new ItemModel(UUID.randomUUID(), mutable, WorkItemStatus.NOT_STARTED, 0.0);

		mutable.add(CONFIDENT);

		assertThat(item.estimates()).hasSize(1);
	}

	@Test
	void refusesEffortThatIsNotAnAmountOfWork() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.IN_PROGRESS, -1.0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.IN_PROGRESS, Double.NaN));
	}

	@Test
	void refusesAnItemThatSaysNothingAboutItself() {
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new ItemModel(null, List.of(), WorkItemStatus.NOT_STARTED, 0.0));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new ItemModel(UUID.randomUUID(), List.of(), null, 0.0));
		assertThatExceptionOfType(NullPointerException.class)
			.isThrownBy(() -> new ItemModel(UUID.randomUUID(), null, WorkItemStatus.NOT_STARTED, 0.0));
	}

	// What this item says about work nobody has thought of yet -----------------

	/**
	 * <strong>The reference class is the estimate, not what is left of it.</strong> Work
	 * nobody has thought of has not been started, so an item serving as the model for it
	 * is asked what it was estimated at — never what it has left, which is what
	 * {@link ItemModel#sample} answers and which would be zero for exactly the finished
	 * work that makes the best reference.
	 */
	@Test
	void newWorkIsDrawnFromTheEstimateRatherThanFromWhatIsLeftOfIt() {
		LogNormalFit fit = LogNormalFit.from(8.0, 40.0);
		ItemModel finished = item(WorkItemStatus.DONE, 0.0, fit);
		ItemModel wellOverrun = item(WorkItemStatus.IN_PROGRESS, 200.0, fit);

		assertThat(finished.sample(new Random(SEED))).isZero();
		assertThat(mean(newWorkLike(finished))).isCloseTo(fit.mean(), withinPercentage(1.0));
		assertThat(mean(newWorkLike(wellOverrun))).isCloseTo(fit.mean(), withinPercentage(1.0));
	}

	@Test
	void newWorkLikeAnItemTwoPeopleEstimatedIsAMixtureOfBoth() {
		ItemModel argued = item(WorkItemStatus.NOT_STARTED, 0.0, LogNormalFit.from(2.0, 3.0),
				LogNormalFit.from(40.0, 60.0));
		double[] drawn = newWorkLike(argued);

		assertThat(least(drawn)).isLessThan(4.0);
		assertThat(most(drawn)).isGreaterThan(30.0);
	}

	@Test
	void refusesToDescribeNewWorkByWorkNobodyEstimated() {
		ItemModel uncosted = item(WorkItemStatus.NOT_STARTED, 0.0);

		assertThatIllegalArgumentException().isThrownBy(() -> uncosted.sampleAsNewWork(new Random(SEED)));
	}

	private static double[] newWorkLike(ItemModel reference) {
		RandomGenerator random = new Random(SEED);
		double[] values = new double[DRAWS];
		for (int index = 0; index < values.length; index++) {
			values[index] = reference.sampleAsNewWork(random);
		}
		return values;
	}

	private static ItemModel item(WorkItemStatus status, double spent, LogNormalFit... estimates) {
		return new ItemModel(UUID.randomUUID(), List.of(estimates), status, spent);
	}

	/** One draw, for the cases whose answer does not depend on the generator at all. */
	private static double only(ItemModel item) {
		return item.sample(new Random(SEED));
	}

	private static double[] draws(ItemModel item) {
		return draws(item, SEED);
	}

	private static double[] draws(ItemModel item, long seed) {
		RandomGenerator random = new Random(seed);
		double[] values = new double[DRAWS];
		for (int index = 0; index < values.length; index++) {
			values[index] = item.sample(random);
		}
		return values;
	}

	/**
	 * How much work is left on average, given that {@code spent} has already gone in —
	 * worked out rather than sampled, so that the sampler has something to be wrong
	 * against.
	 */
	private static double meanRemainingAfter(LogNormalFit fit, double spent) {
		double sigma = fit.sigma();
		double logSpent = Math.log(spent);
		double conditional = fit.mean() * Normal.cdf((fit.mu() + sigma * sigma - logSpent) / sigma)
				/ Normal.cdf((fit.mu() - logSpent) / sigma);
		return conditional - spent;
	}

	private static double least(double[] values) {
		return Arrays.stream(values).min().orElseThrow();
	}

	private static double most(double[] values) {
		return Arrays.stream(values).max().orElseThrow();
	}

	private static double mean(double[] values) {
		double total = 0.0;
		for (double value : values) {
			total += value;
		}
		return total / values.length;
	}

	private static double variance(double[] values) {
		double mean = mean(values);
		double total = 0.0;
		for (double value : values) {
			total += (value - mean) * (value - mean);
		}
		return total / (values.length - 1);
	}

}
