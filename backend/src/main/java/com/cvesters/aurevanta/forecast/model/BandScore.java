package com.cvesters.aurevanta.forecast.model;

/**
 * One three-point range, measured against what the work actually took.
 *
 * <p>
 * <strong>Two readings of the same pair of numbers, and they are deliberately not the
 * same kind of thing.</strong> {@link #inside()} is a fact about what somebody claimed:
 * the actual either fell between the two ends they wrote down or it did not, and no model
 * is involved in deciding. {@link #z()} is a fact about a <em>fitted</em> distribution:
 * how far out the outcome landed, in the units that distribution measures in.
 *
 * <p>
 * <strong>The hit rate never goes through the fit, and that is what makes it the number
 * to put in front of a sceptic.</strong> It would give the same answer if this product
 * replaced the log-normal tomorrow, so a change to how a range is modelled can move the
 * corrections M8 offers and cannot move the headline it offers them beside. It also
 * disposes of the degenerate case without a special path: three identical numbers make
 * {@code sigma} zero and leave {@code z} undefined, but whether the actual fell between
 * the two ends is perfectly well defined, and the answer is almost always no. Somebody
 * who claimed certainty and was wrong scores a miss, which is correct.
 *
 * <p>
 * <strong>The stated middle plays no part at all</strong>, which is why it is not an
 * argument. The band is P10 to P90, and the fit takes the two ends — {@link LogNormalFit}
 * is explicit that three points over-determine two parameters and that the middle is the
 * number people answer fastest and think about least. Whether the stated middle agrees
 * with its own ends is a different question and {@link EstimateQuality} already answers
 * it.
 *
 * @param inside whether the actual fell within the range, ends included. A range somebody
 * hit exactly at one end is a range that contained the outcome.
 * @param belowP10 and {@code aboveP90} are which way a miss went, kept apart because they
 * are different problems: misses that are all above P90 are optimism, and misses on both
 * sides are a band too tight.
 * @param modelled whether the two ends imply a distribution at all. False for a range
 * with no width, where {@code z} is {@code 0} and means nothing — the
 * {@link Contribution#NONE} shape, and the zero is not a claim that the outcome landed in
 * the middle.
 * @param z how far out the actual landed, in standard deviations of the fitted logarithm.
 * Zero is the fitted median, which is the geometric mean of the two ends and so not, in
 * general, the number in the middle box.
 */
public record BandScore(boolean inside, boolean belowP10, boolean aboveP90, boolean modelled, double z) {

	/**
	 * Scores one range against one actual, both in the hours they were given in.
	 *
	 * <p>
	 * The refusals are {@link LogNormalFit#from}'s, plus one of its own for the actual —
	 * and like that class's, none of them is reachable through the API, where
	 * {@code @Positive} and {@code estimate_out_of_order} between them guarantee
	 * {@code 0 < p10 <= p90} and {@code actual > 0} long before anything gets here. They
	 * exist because this is a pure function a test calls directly, and because the
	 * alternative is worse than an unused branch: {@code Math.log} of nothing is a silent
	 * {@code -Infinity} that would travel all the way into a median before anybody
	 * noticed it.
	 * @throws IllegalArgumentException if the range does not ascend or starts at nothing,
	 * or if the work took no time at all
	 */
	public static BandScore of(double p10Hours, double p90Hours, double actualHours) {
		if (!(actualHours > 0.0)) {
			throw new IllegalArgumentException("Work that was done took some time, but this took " + actualHours);
		}
		LogNormalFit fit = LogNormalFit.from(p10Hours, p90Hours);
		boolean below = actualHours < p10Hours;
		boolean above = actualHours > p90Hours;
		if (fit.sigma() <= 0.0) {
			return new BandScore(!below && !above, below, above, false, 0.0);
		}
		double z = (Math.log(actualHours) - fit.mu()) / fit.sigma();
		return new BandScore(!below && !above, below, above, true, z);
	}

	/**
	 * Where the outcome landed on the estimator's own scale, between 0 and 1.
	 *
	 * <p>
	 * This is the whole of the evidence in one number, and {@link #inside()} is one bit
	 * of it: a hit is a percentile between 0.1 and 0.9. What the extra resolution buys is
	 * the difference between work that ran a minute over its P90 and work that took twice
	 * it, which a hit rate cannot see at all.
	 *
	 * <p>
	 * Derived rather than stored, so the two cannot come to disagree — the shape
	 * {@link Contribution} has for the same reason.
	 * @throws IllegalStateException if the range had no width, and so no scale to land on
	 */
	public double percentile() {
		if (!this.modelled) {
			throw new IllegalStateException("A range with no width puts no outcome anywhere on it");
		}
		return Normal.cdf(this.z);
	}

}
