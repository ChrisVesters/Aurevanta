package com.cvesters.aurevanta.forecast.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * <strong>{@code theCanonicalGarbagePassesBothChecks} is the one to read, and it is a
 * test that asserts a failure.</strong> `docs/design/elicitation.md` opens with the
 * measurement it carries: every Fibonacci triple agrees with itself to within a few
 * percent and sits just above the ratio rule, so neither check catches the estimate this
 * whole design is named after. That is not a bug to fix by moving a threshold — it is why
 * the order the three questions are asked in is the defence and these two are a backstop.
 * The numbers are in the assertion rather than only in the plan so that anybody who
 * "fixes" a threshold has to come here and read why it is where it is.
 */
class EstimateQualityTests {

	/**
	 * <strong>The measurement this work is built on.</strong> Consistency within a few
	 * percent of perfect and a P90 about 1.6 times the P50, on all four — coherent
	 * garbage, invisible to anything that looks at three numbers on their own.
	 */
	@Test
	void theCanonicalGarbagePassesBothChecks() {
		assertThat(EstimateQuality.of(3, 5, 8)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(1.02, within(0.005));
			assertThat(graded.inconsistent()).isFalse();
			assertThat(graded.overconfident()).isFalse();
		});
		assertThat(EstimateQuality.of(2, 3, 5)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(0.95, within(0.005));
			assertThat(graded.inconsistent()).isFalse();
			assertThat(graded.overconfident()).isFalse();
		});
		assertThat(EstimateQuality.of(5, 8, 13)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(0.99, within(0.005));
			assertThat(graded.inconsistent()).isFalse();
			assertThat(graded.overconfident()).isFalse();
		});
		// The tightest of the four, and it lands exactly on the boundary rather than
		// inside it: a P90 of 3 against a P50 of 2 is 1.5, and the rule is *below* 1.5.
		assertThat(EstimateQuality.of(1, 2, 3)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(1.155, within(0.001));
			assertThat(graded.inconsistent()).isFalse();
			assertThat(graded.overconfident()).isFalse();
		});
	}

	/**
	 * Either side of the ratio, and the boundary itself. A band 1.4 times its middle
	 * reads as somebody who did not think about the bad case; 1.6 does not, which is what
	 * lets the Fibonacci triples through and is the trade that keeps this warning rare
	 * enough to be read.
	 */
	@Test
	void aBandTooTightToHaveBeenThoughtAboutIsFlaggedAndAWiderOneIsNot() {
		assertThat(EstimateQuality.of(6, 10, 14).overconfident()).isTrue();
		assertThat(EstimateQuality.of(6, 10, 16).overconfident()).isFalse();
		assertThat(EstimateQuality.of(6, 10, 15).overconfident()).isFalse();
	}

	/**
	 * And either side of the other one. A quarter is far enough that a range somebody
	 * thought about survives it, and close enough to catch a middle that was not chosen
	 * with the ends in view — 5/10/40 implies a middle of 14.1, so 10 is a long way
	 * under.
	 */
	@Test
	void aMiddleFarFromTheOneItsOwnEndsImplyIsFlagged() {
		assertThat(EstimateQuality.of(5, 10, 40)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(0.707, within(0.001));
			assertThat(graded.inconsistent()).isTrue();
		});
		assertThat(EstimateQuality.of(8, 16, 40)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(0.894, within(0.001));
			assertThat(graded.inconsistent()).isFalse();
		});
	}

	/**
	 * The other direction, since the check is a distance and not a shortfall: 2/7/8
	 * implies a middle of 4 and states 7, which is somebody whose typical case sits
	 * almost at their own bad one.
	 */
	@Test
	void aMiddleFarAboveTheOneItsOwnEndsImplyIsFlaggedToo() {
		assertThat(EstimateQuality.of(2, 7, 8)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(1.75, within(0.001));
			assertThat(graded.inconsistent()).isTrue();
		});
	}

	/**
	 * <strong>Certainty is not an error.</strong> The plan schema accepts three identical
	 * numbers on purpose — it is somebody saying they know — and `LogNormalFit` fits it
	 * as a point mass rather than refusing. So this grades it: perfectly consistent, and
	 * tight, which is exactly what it is.
	 */
	@Test
	void threeIdenticalNumbersAreConsistentAndTight() {
		assertThat(EstimateQuality.of(4, 4, 4)).satisfies((graded) -> {
			assertThat(graded.consistency()).isCloseTo(1.0, within(1e-12));
			assertThat(graded.inconsistent()).isFalse();
			assertThat(graded.overconfident()).isTrue();
		});
	}

	/**
	 * The two refusals are `LogNormalFit`'s, inherited rather than repeated: an estimate
	 * of nothing has no logarithm, and a range the wrong way round has no fit. Neither is
	 * reachable through the API, where `@Positive` and `estimate_out_of_order` come
	 * first.
	 */
	@Test
	void refusesARangeThatCannotBeFitted() {
		assertThatIllegalArgumentException().isThrownBy(() -> EstimateQuality.of(0, 5, 12));
		assertThatIllegalArgumentException().isThrownBy(() -> EstimateQuality.of(12, 5, 3));
	}

	/**
	 * The bounds are published so that nothing else has to hold a copy, and this is what
	 * fails if one of them is quietly moved somewhere else instead.
	 */
	@Test
	void theThresholdsAreStatedHere() {
		assertThat(EstimateQuality.CONSISTENT_ENOUGH).isEqualTo(0.25);
		assertThat(EstimateQuality.TIGHT_BAND).isEqualTo(1.5);
	}

}
