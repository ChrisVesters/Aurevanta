package com.cvesters.aurevanta.forecast.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * What a three-point estimate becomes, and what it says about itself on the way.
 *
 * <p>
 * The first test is the one that matters: a fit has to hand back the two numbers it was
 * given, or every percentile downstream of it is answering a question nobody asked. The
 * rest check the moments the closed-form oracle will be built on in step 4, and the two
 * ways an estimate can legally be strange — a range of nothing, and a middle that argues
 * with its own ends.
 */
class LogNormalFitTests {

	/** The percentile of one end, as the caller of {@code quantile} would ask for it. */
	private static final double LOW = 0.10;

	private static final double HIGH = 0.90;

	/**
	 * <strong>Relative, not absolute, and the reason is worth knowing.</strong> The fit
	 * itself is exact — it is two logarithms and a division — but recovering an end runs
	 * back through {@link Normal#quantile}, whose error is a *relative* one of about a
	 * part in a billion. That lands as a relative error on an estimate of any size, so a
	 * fixed tolerance would pass at eight hours and fail at eight thousand for no reason
	 * anybody could act on.
	 */
	private static final double RECOVERY_ERROR = 1e-6;

	@Test
	void handsBackTheTwoEndsItWasGiven() {
		LogNormalFit fit = LogNormalFit.from(8.0, 40.0);

		assertThat(percentile(fit, LOW)).isCloseTo(8.0, withinPercentage(RECOVERY_ERROR));
		assertThat(percentile(fit, HIGH)).isCloseTo(40.0, withinPercentage(RECOVERY_ERROR));
	}

	/**
	 * A tenth of an hour and a plan nobody could finish, both of which the column accepts
	 * — {@code numeric(12, 2)} reaches ten billion — so the fit has to survive them
	 * without overflowing or losing the ends it was given.
	 */
	@ParameterizedTest
	@ValueSource(doubles = { 0.01, 0.5, 3.0, 200.0, 1e6, 1e10 })
	void handsThemBackWhateverScaleTheyAreOn(double low) {
		LogNormalFit fit = LogNormalFit.from(low, low * 4.0);

		assertThat(percentile(fit, LOW)).isCloseTo(low, withinPercentage(RECOVERY_ERROR));
		assertThat(percentile(fit, HIGH)).isCloseTo(low * 4.0, withinPercentage(RECOVERY_ERROR));
		assertThat(fit.mean()).isFinite().isPositive();
		assertThat(fit.variance()).isFinite().isPositive();
	}

	/**
	 * The implied middle is the geometric mean of the ends, which is what fitting from
	 * the two tails means: it is below the arithmetic middle, and further below it the
	 * wider the range.
	 */
	@Test
	void theMiddleItImpliesIsTheGeometricMeanOfTheEnds() {
		assertThat(LogNormalFit.from(8.0, 32.0).median()).isCloseTo(16.0, within(1e-12));
		assertThat(LogNormalFit.from(2.0, 50.0).median()).isCloseTo(10.0, within(1e-12));
	}

	/**
	 * Both moments against the analytic formulae, which is the arithmetic step 4's oracle
	 * adds up: variances of independent draws sum exactly, so a chain of tasks has a mean
	 * and a variance the sampler can be measured against rather than trusted about.
	 */
	@Test
	void itsMomentsAreTheAnalyticOnes() {
		LogNormalFit fit = LogNormalFit.from(4.0, 36.0);
		double mu = fit.mu();
		double sigma = fit.sigma();

		assertThat(fit.mean()).isCloseTo(Math.exp(mu + sigma * sigma / 2.0), within(1e-12));
		assertThat(fit.variance()).isCloseTo((Math.exp(sigma * sigma) - 1.0) * Math.exp(2.0 * mu + sigma * sigma),
				within(1e-9));
	}

	/**
	 * The gap that makes summing P50s understate — worth pinning, not merely asserting.
	 */
	@Test
	void theAverageOutcomeSitsAboveTheMiddleOne() {
		LogNormalFit narrow = LogNormalFit.from(18.0, 22.0);
		LogNormalFit wide = LogNormalFit.from(2.0, 30.0);

		assertThat(narrow.mean()).isGreaterThan(narrow.median());
		assertThat(wide.mean()).isGreaterThan(wide.median());
		assertThat(wide.mean() / wide.median()).isGreaterThan(narrow.mean() / narrow.median());
	}

	/**
	 * Somebody who is certain. M2 accepts three identical numbers, so this has to fit
	 * rather than refuse, and it has to come out as a point mass rather than as something
	 * very nearly one.
	 */
	@Test
	void aRangeOfNothingIsAPointMass() {
		LogNormalFit fit = LogNormalFit.from(12.0, 12.0);

		assertThat(fit.sigma()).isZero();
		assertThat(fit.median()).isCloseTo(12.0, within(1e-12));
		assertThat(fit.mean()).isCloseTo(12.0, within(1e-12));
		assertThat(fit.variance()).isZero();
	}

	@Test
	void aMiddleThatAgreesWithItsEndsScoresOne() {
		assertThat(LogNormalFit.from(8.0, 32.0).consistency(16.0)).isCloseTo(1.0, within(1e-12));
	}

	/**
	 * The signal M5 will build on. An estimator whose middle sits high has a long left
	 * tail they have not thought about, and one whose middle sits low has a range they
	 * padded at the top; the number says which, and this milestone does nothing else with
	 * it.
	 */
	@Test
	void aMiddleThatArguesWithItsEndsSaysWhichWay() {
		LogNormalFit fit = LogNormalFit.from(8.0, 32.0);

		assertThat(fit.consistency(24.0)).isGreaterThan(1.0);
		assertThat(fit.consistency(10.0)).isLessThan(1.0);
	}

	@Test
	void refusesAnEstimateThatStartsAtOrBelowNothing() {
		assertThatIllegalArgumentException().isThrownBy(() -> LogNormalFit.from(0.0, 10.0));
		assertThatIllegalArgumentException().isThrownBy(() -> LogNormalFit.from(-1.0, 10.0));
	}

	/**
	 * Unreachable through the API — {@code estimate_out_of_order} answers it long before
	 * here — but this is a pure function, so it is reachable from exactly here.
	 */
	@Test
	void refusesAnEstimateTheWrongWayRound() {
		assertThatIllegalArgumentException().isThrownBy(() -> LogNormalFit.from(30.0, 10.0));
	}

	/**
	 * Where the fit says its own percentile lies, which is what recovering an end means.
	 */
	private static double percentile(LogNormalFit fit, double p) {
		return Math.exp(fit.mu() + fit.sigma() * Normal.quantile(p));
	}

}
