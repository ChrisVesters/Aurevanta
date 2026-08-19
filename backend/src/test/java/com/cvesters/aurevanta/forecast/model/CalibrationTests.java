package com.cvesters.aurevanta.forecast.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

/**
 * What a set of ranges turned out to be worth, checked against arithmetic that exists
 * outside this codebase.
 *
 * <p>
 * <strong>The oracle here is exactness rather than convergence</strong>, which is what
 * makes it worth more than a sampled one. Ten outcomes placed at the midpoints of the
 * deciles of a known log-normal are a perfectly calibrated estimator with no randomness
 * anywhere: the hit rate has to be exactly eight in ten, one miss each way, and the
 * median has to sit exactly in the middle. Those actuals are written out below rather
 * than computed from {@link Normal#quantile}, because a case built with the same function
 * it then checks is a function marking its own homework — the objection {@code Normal}'s
 * own documentation makes about refining one of its halves against the other.
 *
 * <p>
 * The one number that is neither zero nor a round fraction is the multiplier on that set,
 * and it is <strong>0.989 rather than 1</strong>. Ten stratified points under-represent
 * the tails, so a perfectly calibrated sample of them does not read as perfectly
 * calibrated — writing 1.0 into that assertion is the mistake this test exists to
 * prevent, along with the neighbouring one of dividing by {@code n} instead of
 * {@code n − 1}, which reads 0.938 on the same numbers.
 */
class CalibrationTests {

	private static final double P10 = 10.0;

	private static final double P90 = 40.0;

	/**
	 * A perfectly calibrated estimator's ten outcomes: the P10-to-P90 band above, with
	 * the actuals landing at the 5th, 15th … 95th percentiles of the log-normal those two
	 * ends imply.
	 *
	 * <p>
	 * Computed outside this codebase from {@code erf}, so that agreement with Acklam's
	 * approximation inside {@link Normal} is evidence rather than a tautology. Two of
	 * them fall outside the band by construction — 8.22 below the low end and 48.69 above
	 * the high one — which is what makes the target hit rate exactly eight in ten and
	 * gives one miss in each direction to count.
	 */
	private static final double[] PERFECTLY_CALIBRATED = { 8.21603307852883, 11.417657801137004, 13.886594598600988,
			16.237537330804738, 18.685846608255925, 21.406576238470734, 24.63427746775047, 28.80475822634719,
			35.03345493155059, 48.6852957110568 };

	/**
	 * The standard deviation of the ten standardised outcomes above, about their own
	 * mean, dividing by nine. The number this whole set exists to pin down.
	 */
	private static final double CALIBRATED_MULTIPLIER = 0.9887069764763528;

	/**
	 * The same figure computed the other way, dividing by ten — which is not the rule.
	 */
	private static final double POPULATION_MULTIPLIER = 0.937969795249138;

	// One range against one outcome --------------------------------------------

	@Test
	void anOutcomeBetweenTheTwoEndsIsAHit() {
		BandScore score = BandScore.of(P10, P90, 20.0);

		assertThat(score.inside()).isTrue();
		assertThat(score.belowP10()).isFalse();
		assertThat(score.aboveP90()).isFalse();
	}

	/** Landing exactly on an end is a range that contained the outcome, both ends. */
	@Test
	void anOutcomeOnEitherEndIsStillInside() {
		assertThat(BandScore.of(P10, P90, P10).inside()).isTrue();
		assertThat(BandScore.of(P10, P90, P90).inside()).isTrue();
	}

	/**
	 * The two directions are different problems — all above is optimism, both ways is a
	 * band too tight — so they are counted apart rather than as one miss.
	 */
	@Test
	void aMissSaysWhichWayItWent() {
		assertThat(BandScore.of(P10, P90, 4.0).belowP10()).isTrue();
		assertThat(BandScore.of(P10, P90, 4.0).aboveP90()).isFalse();
		assertThat(BandScore.of(P10, P90, 100.0).aboveP90()).isTrue();
		assertThat(BandScore.of(P10, P90, 100.0).belowP10()).isFalse();
	}

	/**
	 * Zero is the fitted median, which is the geometric mean of the two ends — 20 here,
	 * and so not, in general, whatever somebody wrote in the middle box.
	 */
	@Test
	void theFittedMiddleIsTheGeometricMeanOfTheTwoEnds() {
		assertThat(BandScore.of(P10, P90, 20.0).z()).isCloseTo(0.0, within(1e-12));
	}

	/**
	 * And an outcome landing on either end is exactly that end of the fit — the constant
	 * every estimate in this product is fitted through, which is what says the scale
	 * being measured on is the one the fit defines rather than one this class invented.
	 */
	@Test
	void anOutcomeOnAnEndSitsWhereTheFitPutsThatEnd() {
		assertThat(BandScore.of(P10, P90, P90).z()).isCloseTo(Normal.P90_Z, within(1e-12));
		assertThat(BandScore.of(P10, P90, P10).z()).isCloseTo(-Normal.P90_Z, within(1e-12));
	}

	/**
	 * <strong>The headline never goes through the fit</strong>, which is what lets a
	 * change to how a range is modelled move the corrections and not the hit rate. Sixty
	 * times the hours is the same estimate in different units, and every one of these is
	 * untouched by it — the invariance {@code ContributionsTests} asserts, for the same
	 * reason.
	 */
	@Test
	void scoringIsTheSameInMinutesAsInHours() {
		BandScore hours = BandScore.of(P10, P90, 33.0);
		BandScore minutes = BandScore.of(P10 * 60.0, P90 * 60.0, 33.0 * 60.0);

		assertThat(minutes.inside()).isEqualTo(hours.inside());
		assertThat(minutes.z()).isCloseTo(hours.z(), within(1e-12));
	}

	/**
	 * Three identical numbers are somebody saying they are certain, which the plan schema
	 * accepts on purpose. Whether the outcome fell between the two ends is still a
	 * perfectly good question; where it fell on a scale is not, because there is no
	 * scale.
	 */
	@Test
	void aClaimOfCertaintyIsScoredWithoutBeingModelled() {
		BandScore missed = BandScore.of(8.0, 8.0, 9.0);

		assertThat(missed.inside()).isFalse();
		assertThat(missed.aboveP90()).isTrue();
		assertThat(missed.modelled()).isFalse();
		assertThat(missed.z()).isZero();
	}

	/**
	 * Certainty misses in both directions, and being early is still being wrong about it.
	 */
	@Test
	void aClaimOfCertaintyCanBeMissedFromUnderneath() {
		BandScore early = BandScore.of(8.0, 8.0, 6.0);

		assertThat(early.inside()).isFalse();
		assertThat(early.belowP10()).isTrue();
		assertThat(early.aboveP90()).isFalse();
		assertThat(early.modelled()).isFalse();
	}

	@Test
	void aClaimOfCertaintyThatCameTrueIsAHit() {
		BandScore hit = BandScore.of(8.0, 8.0, 8.0);

		assertThat(hit.inside()).isTrue();
		assertThat(hit.modelled()).isFalse();
	}

	@Test
	void refusesWorkThatTookNoTimeAtAll() {
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> BandScore.of(P10, P90, 0.0));
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> BandScore.of(P10, P90, -1.0));
	}

	@Test
	void refusesARangeThatDoesNotAscend() {
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> BandScore.of(P90, P10, 20.0));
	}

	// A whole record -----------------------------------------------------------

	/**
	 * <strong>The oracle.</strong> Ten outcomes at the decile midpoints of their own
	 * fitted distribution: eight in ten inside, one miss each way, and the median exactly
	 * in the middle. Every figure here is a target rather than an approximation of one.
	 */
	@Test
	void aPerfectlyCalibratedSetScoresTheTargetExactly() {
		Calibration record = calibrated();

		assertThat(record.estimates()).isEqualTo(10);
		assertThat(record.hits()).isEqualTo(8);
		assertThat(record.hitRate().rate()).isEqualTo(0.8);
		assertThat(record.belowP10()).isEqualTo(1);
		assertThat(record.aboveP90()).isEqualTo(1);
		assertThat(record.medianPercentile()).isCloseTo(0.5, within(1e-9));
	}

	/**
	 * <strong>0.989 and not 1</strong>, because ten stratified points under-represent the
	 * tails. A test asserting 1.0 here would be asserting something false about a case
	 * chosen to be perfect, and would then have to be "fixed" by loosening a tolerance
	 * until it also stopped catching a real bias.
	 */
	@Test
	void aPerfectlyCalibratedSetOfTenReadsJustUnderOne() {
		assertThat(calibrated().bandWidthMultiplier()).isCloseTo(CALIBRATED_MULTIPLIER, within(1e-6));
	}

	/**
	 * The spread of a sample of somebody's work, not of a population that happens to be
	 * all of it. The two are far enough apart at these counts to be worth pinning: 0.989
	 * against 0.938.
	 */
	@Test
	void theSpreadIsTheSampleFormAndNotThePopulationOne() {
		assertThat(calibrated().bandWidthMultiplier()).isNotCloseTo(POPULATION_MULTIPLIER, within(1e-3));
	}

	/**
	 * <strong>Decision 6 made into a test.</strong> The two statistics have to move
	 * independently, or the pair says no more than either alone. Doubling every outcome
	 * against unchanged ranges is pure bias — and against a band whose ends are a factor
	 * of four apart it is exactly one 90th-percentile step, because the doubling is half
	 * the band's own width in logarithms.
	 */
	@Test
	void biasMovesWithoutTheSpreadMoving() {
		Calibration doubled = new Calibration();
		for (double actual : PERFECTLY_CALIBRATED) {
			doubled.scored(BandScore.of(P10, P90, actual * 2.0));
		}

		assertThat(doubled.medianPercentile()).isCloseTo(0.9, within(1e-9));
		assertThat(doubled.bandWidthMultiplier()).isCloseTo(CALIBRATED_MULTIPLIER, within(1e-9));
	}

	/**
	 * And the other way round: ranges twice as wide in logarithms leave the outcomes
	 * where they were on the scale's middle and halve how far out they measure. An
	 * estimator whose bands are twice what they need to be is told 0.5.
	 */
	@Test
	void spreadMovesWithoutTheBiasMoving() {
		Calibration padded = new Calibration();
		for (double actual : PERFECTLY_CALIBRATED) {
			// Same fitted median, twice the sigma: the ends move out by the square of the
			// original ratio, which is what doubling a log-width means.
			padded.scored(BandScore.of(P10 * P10 / 20.0, P90 * P90 / 20.0, actual));
		}

		assertThat(padded.medianPercentile()).isCloseTo(0.5, within(1e-9));
		assertThat(padded.bandWidthMultiplier()).isCloseTo(CALIBRATED_MULTIPLIER / 2.0, within(1e-6));
	}

	/**
	 * Every outcome at its own high end: the same place on every scale, and every one a
	 * hit.
	 */
	@Test
	void aSetThatAlwaysLandsOnItsOwnHighEndSaysSo() {
		Calibration record = new Calibration();
		record.scored(BandScore.of(10.0, 40.0, 40.0));
		record.scored(BandScore.of(3.0, 9.0, 9.0));
		record.scored(BandScore.of(100.0, 120.0, 120.0));

		assertThat(record.hitRate().rate()).isEqualTo(1.0);
		assertThat(record.medianPercentile()).isCloseTo(0.9, within(1e-9));
		// No spread at all: every outcome landed at the identical point on its own scale.
		assertThat(record.bandWidthMultiplier()).isCloseTo(0.0, within(1e-9));
	}

	/**
	 * An odd count takes the middle observation rather than splitting two, which is the
	 * other half of the median and the one an even-sized oracle cannot reach.
	 */
	@Test
	void anOddNumberOfOutcomesTakesTheMiddleOne() {
		Calibration record = new Calibration();
		record.scored(BandScore.of(P10, P90, 12.0));
		record.scored(BandScore.of(P10, P90, 20.0));
		record.scored(BandScore.of(P10, P90, 33.0));

		assertThat(record.medianPercentile()).isCloseTo(0.5, within(1e-9));
	}

	/** Order of arrival is not order of outcome, and the median is about the second. */
	@Test
	void theMedianDoesNotDependOnTheOrderTheyArrived() {
		Calibration forwards = new Calibration();
		Calibration backwards = new Calibration();
		for (int at = 0; at < PERFECTLY_CALIBRATED.length; at++) {
			forwards.scored(BandScore.of(P10, P90, PERFECTLY_CALIBRATED[at]));
			backwards.scored(BandScore.of(P10, P90, PERFECTLY_CALIBRATED[PERFECTLY_CALIBRATED.length - 1 - at]));
		}

		assertThat(backwards.medianPercentile()).isEqualTo(forwards.medianPercentile());
		assertThat(backwards.bandWidthMultiplier()).isCloseTo(forwards.bandWidthMultiplier(), within(1e-12));
	}

	/**
	 * A claim of certainty counts in the rate and cannot count in the corrections, and
	 * the two denominators are published so that they can be seen to differ for a reason.
	 */
	@Test
	void aClaimOfCertaintyCountsInTheRateAndNotInTheCorrections() {
		Calibration record = calibrated();
		record.scored(BandScore.of(8.0, 8.0, 9.0));

		assertThat(record.estimates()).isEqualTo(11);
		assertThat(record.pointEstimates()).isEqualTo(1);
		assertThat(record.hits()).isEqualTo(8);
		assertThat(record.bandWidthMultiplier()).isCloseTo(CALIBRATED_MULTIPLIER, within(1e-12));
	}

	@Test
	void anEmptyRecordSaysNothingRatherThanZero() {
		Calibration record = new Calibration();

		assertThat(record.estimates()).isZero();
		assertThat(record.pointEstimates()).isZero();
		assertThat(record.hitRate().measured()).isFalse();
		assertThat(record.corrected()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(record::medianPercentile);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(record::bandWidthMultiplier);
	}

	/**
	 * One outcome has a median and no spread. Both are withheld together, because a bias
	 * published without a spread beside it is exactly the half of this record that reads
	 * as a target.
	 */
	@Test
	void oneOutcomeCorrectsNothing() {
		Calibration record = new Calibration();
		record.scored(BandScore.of(P10, P90, 20.0));

		assertThat(record.hitRate().rate()).isEqualTo(1.0);
		assertThat(record.corrected()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(record::medianPercentile);
	}

	/**
	 * A record made only of claims of certainty has a rate and no scale to correct on.
	 */
	@Test
	void aSetOfNothingButCertaintyHasARateAndNoCorrection() {
		Calibration record = new Calibration();
		record.scored(BandScore.of(8.0, 8.0, 9.0));
		record.scored(BandScore.of(4.0, 4.0, 4.0));

		assertThat(record.estimates()).isEqualTo(2);
		assertThat(record.pointEstimates()).isEqualTo(2);
		assertThat(record.hits()).isEqualTo(1);
		assertThat(record.corrected()).isFalse();
	}

	// How little a rate says ---------------------------------------------------

	/**
	 * The table this work's screens are designed around, asserted so that a change to the
	 * interval is a change to a document as well as to a function. Four in five and
	 * thirty-two in forty are both 80% and only one of them means anything.
	 */
	@Test
	void theIntervalIsWhatSaysHowLittleASmallSampleSays() {
		assertBounds(new Proportion(4, 5), 0.514, 0.938);
		assertBounds(new Proportion(8, 10), 0.602, 0.914);
		assertBounds(new Proportion(32, 40), 0.708, 0.868);
		assertBounds(new Proportion(18, 40), 0.353, 0.551);
		assertBounds(new Proportion(45, 100), 0.388, 0.514);
	}

	/**
	 * <strong>What the textbook interval loses, kept in this file so that reinstating it
	 * is a failing test rather than a tidier-looking method.</strong> At four in five it
	 * runs three points past certainty, and at five in five it collapses to no width at
	 * all and claims a team is calibrated on the evidence of five tasks.
	 */
	@Test
	void theNormalApproximationIsWrongInBothOfTheCasesThisWillBeReadIn() {
		assertThat(naiveHigh(4, 5)).isGreaterThan(1.0);
		assertThat(new Proportion(4, 5).high()).isLessThan(1.0);

		assertThat(naiveHigh(5, 5) - naiveLow(5, 5)).isZero();
		assertThat(new Proportion(5, 5).low()).isLessThan(0.8);
	}

	/**
	 * <strong>Every bound is a probability, at every count.</strong> Algebraically both
	 * ends land exactly on 0 and 1 when every outcome falls one way; binary arithmetic
	 * misses that by about one part in 10^16 and lands on either side of it. Below is
	 * invisible — 0.9999999999999999 prints as 100% — and above is not, because
	 * 1.0000000000000002 is a number that cannot be a probability at all. So the bounds
	 * are clamped, and this asserts the property over every count a real record could
	 * reach rather than over a handful of chosen ones.
	 */
	@Test
	void everyBoundIsAProbability() {
		for (int of = 1; of <= 200; of++) {
			for (int hits = 0; hits <= of; hits++) {
				Proportion proportion = new Proportion(hits, of);
				assertThat(proportion.low()).as("low of %s", proportion).isBetween(0.0, proportion.rate());
				assertThat(proportion.high()).as("high of %s", proportion).isBetween(proportion.rate(), 1.0);
			}
		}
	}

	/**
	 * <strong>And the clamp is load-bearing in both directions</strong>, which is why
	 * this keeps the unclamped arithmetic beside it. Twenty out of twenty comes out at
	 * 1.0000000000000002 and nought out of four at −1.4 × 10^−17: one part in 10^16
	 * either way, which is the size of correction being made. Clamping the normal
	 * approximation would be a correction of three points, and that is the difference
	 * between restoring a property a formula has and hiding the fact that another one
	 * does not.
	 */
	@Test
	void theClampCorrectsOneUlpAndNotAWrongAnswer() {
		assertThat(unclampedHigh(20, 20)).isGreaterThan(1.0).isCloseTo(1.0, within(1e-15));
		assertThat(new Proportion(20, 20).high()).isEqualTo(1.0);

		assertThat(unclampedLow(0, 4)).isLessThan(0.0).isCloseTo(0.0, within(1e-15));
		assertThat(new Proportion(0, 4).low()).isEqualTo(0.0);

		// The same loss showing up as the other property: an upper bound below the rate
		// it
		// is meant to bound, which is incoherent in a way a number outside [0, 1] is not.
		assertThat(unclampedHigh(5, 5)).isLessThan(1.0).isCloseTo(1.0, within(1e-15));
		assertThat(new Proportion(5, 5).high()).isEqualTo(1.0);
	}

	@Test
	void aRateNobodyHasEvidenceForIsNotZero() {
		Proportion nothing = new Proportion(0, 0);

		assertThat(nothing.measured()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(nothing::rate);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(nothing::low);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(nothing::high);
	}

	@Test
	void refusesCountsThatAreNotAProportion() {
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Proportion(-1, 5));
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Proportion(1, -5));
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new Proportion(6, 5));
	}

	/**
	 * The interval narrows as the evidence grows, which is the only reason to publish it.
	 */
	@Test
	void moreOfTheSameEvidenceNarrowsTheInterval() {
		Proportion few = new Proportion(8, 10);
		Proportion many = new Proportion(80, 100);

		assertThat(many.rate()).isEqualTo(few.rate());
		assertThat(many.high() - many.low()).isLessThan(few.high() - few.low());
	}

	// Fixtures -----------------------------------------------------------------

	private static Calibration calibrated() {
		Calibration record = new Calibration();
		for (double actual : PERFECTLY_CALIBRATED) {
			record.scored(BandScore.of(P10, P90, actual));
		}
		return record;
	}

	private static void assertBounds(Proportion proportion, double low, double high) {
		assertThat(proportion.low()).as("low of %s", proportion).isCloseTo(low, within(5e-4));
		assertThat(proportion.high()).as("high of %s", proportion).isCloseTo(high, within(5e-4));
	}

	private static double naiveHigh(int hits, int of) {
		return (double) hits / of + naiveOffset(hits, of);
	}

	private static double naiveLow(int hits, int of) {
		return (double) hits / of - naiveOffset(hits, of);
	}

	private static double naiveOffset(int hits, int of) {
		double observed = (double) hits / of;
		return Normal.P90_Z * Math.sqrt(observed * (1.0 - observed) / of);
	}

	/**
	 * Wilson's own arithmetic without the clamp, so the size of what it corrects is
	 * visible.
	 */
	private static double unclampedHigh(int hits, int of) {
		return unclamped(hits, of, 1.0);
	}

	private static double unclampedLow(int hits, int of) {
		return unclamped(hits, of, -1.0);
	}

	private static double unclamped(int hits, int of, double direction) {
		double z = Normal.P90_Z;
		double spread = z * z;
		double observed = (double) hits / of;
		double denominator = 1.0 + spread / of;
		double centre = (observed + spread / (2.0 * of)) / denominator;
		double offset = (z / denominator) * Math.sqrt(observed * (1.0 - observed) / of + spread / (4.0 * of * of));
		return centre + direction * offset;
	}

}
