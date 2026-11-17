package com.cvesters.aurevanta.forecast.model;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * What several people's ranges on one piece of work say together.
 *
 * <p>
 * Two oracles, and neither is this class. With one estimator the mixture is that
 * estimator's own fit, so it must hand back the ends they gave. With several, its
 * cumulative probability is defined as the mean of theirs — so whatever percentile it
 * returns, reading that mean back at the answer must give the percentile that was asked
 * for.
 */
class EstimatorMixtureTests {

	/** {@link Normal#quantile} is good to about a part in a billion, relatively. */
	private static final double RECOVERY_ERROR = 1e-6;

	private static final LogNormalFit ADA = LogNormalFit.from(2.0, 8.0);

	private static final LogNormalFit BOB = LogNormalFit.from(10.0, 40.0);

	@Test
	void oneEstimatorIsTheirOwnRange() {
		EstimatorMixture mixture = EstimatorMixture.of(List.of(ADA));

		assertThat(mixture.quantile(0.1)).isCloseTo(2.0, withinPercentage(RECOVERY_ERROR));
		assertThat(mixture.quantile(0.5)).isCloseTo(ADA.median(), withinPercentage(RECOVERY_ERROR));
		assertThat(mixture.quantile(0.9)).isCloseTo(8.0, withinPercentage(RECOVERY_ERROR));
	}

	/** Agreement adds nothing: three people saying the same thing is that thing. */
	@Test
	void estimatorsWhoAgreeAreOneRange() {
		EstimatorMixture together = EstimatorMixture.of(List.of(ADA, ADA, ADA));

		assertThat(together.quantile(0.1)).isCloseTo(2.0, withinPercentage(RECOVERY_ERROR));
		assertThat(together.quantile(0.9)).isCloseTo(8.0, withinPercentage(RECOVERY_ERROR));
	}

	/**
	 * The worked example: 2 to 8 hours against 10 to 40. Checked against the definition
	 * rather than against numbers this class produced, and then against the figures a
	 * separate calculation gave for the same pair.
	 */
	@ParameterizedTest
	@ValueSource(doubles = { 0.1, 0.5, 0.9 })
	void readsBackThePercentileItWasAskedFor(double p) {
		EstimatorMixture mixture = EstimatorMixture.of(List.of(ADA, BOB));

		double hours = mixture.quantile(p);

		assertThat(mixture.cdf(hours)).isCloseTo(p, within(1e-9));
	}

	@Test
	void disagreementWidensTheRangeRatherThanAveragingIt() {
		EstimatorMixture mixture = EstimatorMixture.of(List.of(ADA, BOB));

		assertThat(mixture.quantile(0.1)).isCloseTo(2.5, within(0.05));
		assertThat(mixture.quantile(0.5)).isCloseTo(8.9, within(0.05));
		assertThat(mixture.quantile(0.9)).isCloseTo(31.5, within(0.05));
		// Averaging the numbers would have said 6 / 12 / 24, and called under five
		// hours
		// a
		// one-in-twenty chance when one of the two people expects about four.
		assertThat(mixture.cdf(5.0)).isCloseTo(0.333, within(0.001));
	}

	@Test
	void theOrderTheEstimatorsArriveInChangesNothing() {
		EstimatorMixture one = EstimatorMixture.of(List.of(ADA, BOB));
		EstimatorMixture other = EstimatorMixture.of(List.of(BOB, ADA));

		assertThat(other.quantile(0.9)).isEqualTo(one.quantile(0.9));
	}

	/**
	 * Three identical numbers is somebody saying they are certain, and the schema accepts
	 * it. Alone it is its own value at every percentile; beside a range it is a step the
	 * mixture has to climb rather than a division by zero.
	 */
	@Test
	void aCertainEstimateIsItsOwnValueEverywhere() {
		EstimatorMixture mixture = EstimatorMixture.of(List.of(LogNormalFit.from(6.0, 6.0)));

		assertThat(mixture.quantile(0.1)).isCloseTo(6.0, withinPercentage(RECOVERY_ERROR));
		assertThat(mixture.quantile(0.9)).isCloseTo(6.0, withinPercentage(RECOVERY_ERROR));
		assertThat(mixture.cdf(5.99)).isZero();
		assertThat(mixture.cdf(6.0)).isEqualTo(1.0);
	}

	@Test
	void aCertainEstimateBesideARangeCarriesHalfTheWeight() {
		EstimatorMixture mixture = EstimatorMixture.of(List.of(LogNormalFit.from(6.0, 6.0), BOB));

		// Below six only Bob can have finished, and he is unlikely to have: the
		// mixture's
		// chance is half of his. At six the certain half arrives all at once.
		assertThat(mixture.cdf(5.0)).isLessThan(0.05);
		assertThat(mixture.cdf(6.0)).isGreaterThanOrEqualTo(0.5);
		assertThat(mixture.quantile(0.5)).isCloseTo(6.0, withinPercentage(RECOVERY_ERROR));
		assertThat(mixture.quantile(0.9)).isGreaterThan(20.0);
	}

	@Test
	void refusesToCombineNothing() {
		assertThatIllegalArgumentException().isThrownBy(() -> EstimatorMixture.of(List.of()));
	}

}
