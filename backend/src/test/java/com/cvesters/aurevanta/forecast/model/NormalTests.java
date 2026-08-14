package com.cvesters.aurevanta.forecast.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * The two functions the engine is built on, checked against arithmetic that exists
 * outside this codebase.
 *
 * <p>
 * This is the whole justification for not taking a library: every value below is
 * published, so "did we get the numerics right" has an answer. The round-trip test is the
 * one worth understanding — {@code cdf} and {@code quantile} are two rational
 * approximations derived by different people from different forms, and nothing in the
 * code makes them agree. That they do, to a part in a billion, is evidence rather than
 * tautology.
 */
class NormalTests {

	/** Acklam's stated bound on relative error, and so the floor for any comparison. */
	private static final double ACKLAM_ERROR = 1.5e-9;

	@Test
	void theHalfwayPointIsExactlyAHalf() {
		assertThat(Normal.cdf(0.0)).isEqualTo(0.5);
	}

	/**
	 * Values from any published table of the standard normal distribution. The first two
	 * are the ones this product actually uses; the rest walk out into the tail, which is
	 * where an approximation goes wrong if it is going to.
	 */
	@Test
	void matchesThePublishedDistribution() {
		assertThat(Normal.cdf(1.2815515655446004)).isCloseTo(0.90, within(1e-12));
		assertThat(Normal.cdf(-1.2815515655446004)).isCloseTo(0.10, within(1e-12));
		assertThat(Normal.cdf(1.0)).isCloseTo(0.8413447460685429, within(1e-12));
		assertThat(Normal.cdf(-1.959963984540054)).isCloseTo(0.025, within(1e-12));
		assertThat(Normal.cdf(3.0)).isCloseTo(0.9986501019683699, within(1e-12));
	}

	/**
	 * <strong>Hart's algorithm is accurate in absolute terms, not relative ones, and the
	 * difference only shows up out here.</strong> Near the middle both readings agree; by
	 * the time the answer is 10^-16 an absolute error of 10^-16 is the whole of it. So
	 * these are checked as percentages, and the achieved accuracy is stated rather than
	 * guessed at: about a part in 10^9 at eight standard deviations.
	 *
	 * <p>
	 * That matters to whoever uses this next. The truncated draw of decision 5 asks for
	 * the mass an in-progress task has already spent, which is a number near <em>one</em>
	 * where absolute accuracy is what counts — so the tail's relative softness costs
	 * nothing there. It would cost a great deal to anyone using this function to compare
	 * two very small probabilities.
	 */
	@Test
	void isAccurateAbsolutelyRatherThanRelativelyInTheTail() {
		assertThat(Normal.cdf(-6.0)).isCloseTo(9.865876450376946e-10, withinPercentage(1e-6));
		assertThat(Normal.cdf(-8.0)).isCloseTo(6.220960574271786e-16, withinPercentage(1e-5));
	}

	/**
	 * Past the point where Hart's rational form gives way to its continued fraction,
	 * which is a branch nothing else here would exercise — and the point beyond which
	 * there is no answer left to give.
	 */
	@Test
	void staysFiniteWhereTheApproximationChangesShape() {
		assertThat(Normal.cdf(-20.0)).isCloseTo(2.753624e-89, withinPercentage(1e-4));
		assertThat(Normal.cdf(-40.0)).isZero();
		assertThat(Normal.cdf(40.0)).isEqualTo(1.0);
	}

	@Test
	void neverLeavesTheUnitInterval() {
		for (double z = -10.0; z <= 10.0; z += 0.05) {
			assertThat(Normal.cdf(z)).isBetween(0.0, 1.0);
		}
	}

	@Test
	void risesEverywhere() {
		double previous = -1.0;
		for (double z = -6.0; z <= 6.0; z += 0.01) {
			double here = Normal.cdf(z);
			assertThat(here).isGreaterThan(previous);
			previous = here;
		}
	}

	/**
	 * The constant every estimate in this product is fitted through, checked against the
	 * function it was taken from. {@code Normal} states it as a literal precisely so that
	 * this assertion can fail — a computed constant would agree with a broken
	 * {@code quantile} forever.
	 */
	@Test
	void theTenthAndNinetiethPercentilesAreWhereTheFitSaysTheyAre() {
		assertThat(Normal.quantile(0.90)).isCloseTo(Normal.P90_Z, within(1e-8));
		assertThat(Normal.quantile(0.10)).isCloseTo(-Normal.P90_Z, within(1e-8));
	}

	@Test
	void matchesThePublishedQuantiles() {
		assertThat(Normal.quantile(0.5)).isCloseTo(0.0, within(1e-15));
		assertThat(Normal.quantile(0.975)).isCloseTo(1.959963984540054, within(1e-8));
		assertThat(Normal.quantile(0.025)).isCloseTo(-1.959963984540054, within(1e-8));
		assertThat(Normal.quantile(0.8)).isCloseTo(0.8416212335729143, within(1e-8));
		assertThat(Normal.quantile(0.95)).isCloseTo(1.6448536269514722, within(1e-8));
		assertThat(Normal.quantile(0.99)).isCloseTo(2.3263478740408408, within(1e-8));
	}

	/** Both of Acklam's tail regions, which the central form does not reach. */
	@Test
	void matchesThePublishedQuantilesOutInTheTails() {
		assertThat(Normal.quantile(0.001)).isCloseTo(-3.090232306167813, within(1e-8));
		assertThat(Normal.quantile(0.999)).isCloseTo(3.090232306167813, within(1e-8));
		assertThat(Normal.quantile(1e-10)).isCloseTo(-6.361340902404056, within(1e-7));
	}

	/**
	 * Further out than any published table this repository could quote, so the two
	 * functions are asked to agree with each other instead — which is the stronger
	 * statement anyway, since neither knows the other exists.
	 *
	 * <p>
	 * Relatively rather than absolutely, and by a looser bound than the middle gets: the
	 * tail is exponentially steep, so Acklam's part-in-a-billion error on <em>z</em>
	 * becomes about a part in ten million on the probability it names. That is the honest
	 * accuracy of this pair seven standard deviations out, and it is recorded here so
	 * that nobody has to rediscover it.
	 */
	@Test
	void agreesWithItselfSevenDeviationsOut() {
		for (double p : new double[] { 1e-12, 1e-9, 1e-6 }) {
			assertThat(Normal.cdf(Normal.quantile(p))).isCloseTo(p, withinPercentage(1e-5));
			assertThat(Normal.cdf(Normal.quantile(1.0 - p))).isCloseTo(1.0 - p, within(1e-12));
		}
	}

	/**
	 * The test the two functions exist to survive, and the reason {@code quantile} is not
	 * refined against {@code cdf}: neither knows anything about the other, so agreement
	 * is a fact about the mathematics rather than about the code.
	 */
	@Test
	void oneUndoesTheOther() {
		for (double p = 0.001; p < 0.9999; p += 0.001) {
			assertThat(Normal.cdf(Normal.quantile(p))).isCloseTo(p, within(ACKLAM_ERROR));
		}
	}

	@Test
	void isSymmetricAboutTheMiddle() {
		for (double p = 0.001; p < 0.5; p += 0.001) {
			assertThat(Normal.quantile(p)).isCloseTo(-Normal.quantile(1.0 - p), within(1e-9));
		}
	}

	/**
	 * A probability of nought or one names a point that does not exist, so there is
	 * nothing to answer with — and returning an infinity would put one inside a forecast
	 * where it would be discovered several arithmetic steps later.
	 */
	@ParameterizedTest
	@ValueSource(doubles = { 0.0, 1.0, -0.5, 1.5, Double.NaN })
	void refusesAProbabilityThatIsNotOne(double p) {
		assertThatIllegalArgumentException().isThrownBy(() -> Normal.quantile(p));
	}

}
