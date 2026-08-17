package com.cvesters.aurevanta.forecast.model;

/**
 * What is worth questioning about one three-point estimate, and nothing about whether it
 * is allowed.
 *
 * <p>
 * <strong>These are a backstop, and it is important not to mistake them for the
 * defence.</strong> Both checks were run against the failure they exist to catch, and
 * neither catches it: 3/5/8 has a consistency of 1.02 and a P90 1.60 times its P50, so it
 * agrees with itself almost perfectly and clears the ratio rule — as do 2/3/5, 5/8/13 and
 * 1/2/3. A Fibonacci triple is very nearly geometric, which is exactly the shape a
 * log-normal fit expects, so the canonical garbage this product exists to stop is
 * <em>coherent</em> garbage: internally consistent, plausibly shaped, and invisible to
 * any test that can be run on three numbers in isolation. `m5-plan.md` opens with the
 * table.
 *
 * <p>
 * What that leaves these two doing is real and smaller. They catch a middle pasted
 * between two ends that were thought about, and a band so tight it cannot have been meant
 * — both of which are worth saying out loud. What produces an honest range is the
 * <em>order the three questions are asked in</em>, which is a property of how they were
 * collected and leaves no trace in what was stored.
 *
 * <p>
 * <strong>Reported, never enforced.</strong> Nothing here refuses anything. A tight band
 * is sometimes correct — a task done thirty times genuinely has one — and a rule that
 * blocked it would become a specification people learn to satisfy, which is 3/5/8 with an
 * extra step and the product teaching the failure it exists to detect.
 *
 * <p>
 * <strong>Stated once, here, because two places would eventually disagree.</strong> Both
 * thresholds used to be, or would have been, a constant in whichever class needed them:
 * {@code CONSISTENT_ENOUGH} sat in {@code ForecastService} and the ratio rule was about
 * to be born in the browser. One estimate cannot be worth questioning in a forecast and
 * fine on the plan screen, so the bounds live beside the arithmetic they bound — the
 * argument {@code PasswordRules} makes for a credential.
 *
 * @param consistency how far the stated middle sits from the one the two ends imply, as a
 * ratio. Published as a diagnostic rather than as something to print: it is what makes
 * {@link #inconsistent} explicable when somebody asks why a warning fired, and a screen
 * renders the flag.
 * @param inconsistent whether that ratio is far enough from 1 to be worth mentioning
 * @param overconfident whether the band is too tight to have been thought about. A claim
 * about the <em>range</em> and not about the person: it is a pattern that usually means
 * nobody considered what could go wrong, and sometimes means they know the work.
 */
public record EstimateQuality(double consistency, boolean inconsistent, boolean overconfident) {

	/**
	 * How far a stated middle may sit from the one its own two ends imply before this
	 * says so. A quarter either way — far enough that 3/5/8 and its neighbours pass
	 * unremarked, close enough to catch somebody who put 10 in the middle of 5 and 40.
	 */
	public static final double CONSISTENT_ENOUGH = 0.25;

	/**
	 * The smallest ratio of P90 to P50 that reads as somebody having thought about the
	 * bad case, from {@code roadmap.md}: below about this, the pattern almost always
	 * means nobody did.
	 *
	 * <p>
	 * It is not raised to catch the Fibonacci triples, which sit just above it at about
	 * 1.6. A threshold moved until it caught them would fire on nearly every estimate any
	 * team writes, and a warning that fires constantly is one nobody reads — so the
	 * number stays where it was measured to be useful and the framing does the work.
	 */
	public static final double TIGHT_BAND = 1.5;

	/**
	 * Looks at one range, in the hours it was given in.
	 *
	 * <p>
	 * The two ends are fitted the way the engine fits them, so this cannot disagree with
	 * a forecast about what an estimate implies — the same twenty lines, and the same
	 * refusals for a range that does not ascend or starts at nothing.
	 * @throws IllegalArgumentException if the low end is not above zero, or the two ends
	 * are the wrong way round
	 */
	public static EstimateQuality of(double p10Hours, double p50Hours, double p90Hours) {
		double ratio = LogNormalFit.from(p10Hours, p90Hours).consistency(p50Hours);
		return new EstimateQuality(ratio, Math.abs(ratio - 1.0) > CONSISTENT_ENOUGH, p90Hours < TIGHT_BAND * p50Hours);
	}

}
