package com.cvesters.aurevanta.forecast.model;

/**
 * How often something happened, and how little that says when it has not happened often.
 *
 * <p>
 * <strong>The interval is the point of this type existing.</strong> Four hits out of five
 * is a hit rate of 80% and says nothing whatever: the 80% interval on it runs from 51% to
 * 94%, which is consistent with a team that is well calibrated and with one that is badly
 * overconfident. A rate published without its interval invites somebody to act on five
 * completed tasks, and M8's whole output is a number about a small sample.
 *
 * <p>
 * <strong>Wilson's interval rather than the normal approximation</strong>, which fails in
 * exactly the cases this will be read in. At four out of five the textbook form runs to
 * 103%, and a bound above one on screen is the end of the number's credibility; at five
 * out of five it collapses to zero width and claims certainty from five observations.
 * Wilson does neither, and {@code CalibrationTests} keeps the textbook form and asserts
 * both failures so that nobody reinstates it as the simpler-looking arithmetic.
 *
 * <p>
 * <strong>At 80%, which is a coherence decision and not a statistical one.</strong> Every
 * interval this product shows is a P10–P90 band; a 95% interval beside an 80% band is two
 * conventions on one screen and a reader has to hold both. So it is built from
 * {@link Normal#P90_Z}, the same constant every estimate in this product is fitted
 * through.
 *
 * @param hits how many times it happened
 * @param of how many times it could have
 */
public record Proportion(int hits, int of) {

	/**
	 * @throws IllegalArgumentException if the counts are not a proportion — negative, or
	 * more hits than opportunities. Neither is reachable through an accumulator that
	 * counts what it is shown, which is why the check is here rather than there: this is
	 * a public type over two integers and nothing else guards it.
	 */
	public Proportion {
		if (of < 0 || hits < 0) {
			throw new IllegalArgumentException("A proportion cannot be " + hits + " of " + of);
		}
		if (hits > of) {
			throw new IllegalArgumentException(hits + " of " + of + " is more than all of them");
		}
	}

	/**
	 * Whether there is anything here to read a rate from.
	 *
	 * <p>
	 * Nought out of nought is not a rate of nought. Every caller asks this first, and the
	 * three accessors below refuse rather than answering — {@link Normal#quantile}'s rule
	 * for a probability outside its range, which has no answer rather than an extreme
	 * one. A zero returned here would print as "0% of your estimates landed inside their
	 * range" for an organisation that has never finished anything.
	 */
	public boolean measured() {
		return this.of > 0;
	}

	/**
	 * The share, between 0 and 1.
	 * @throws IllegalStateException if nothing has happened
	 */
	public double rate() {
		requireMeasured();
		return (double) this.hits / this.of;
	}

	/**
	 * The bottom of the 80% interval, never below nothing and never above the rate it
	 * describes.
	 * @throws IllegalStateException if nothing has happened
	 */
	public double low() {
		requireMeasured();
		return Math.clamp(centre() - offset(), 0.0, rate());
	}

	/**
	 * The top of the 80% interval, never above certainty and never below the rate it
	 * describes.
	 * @throws IllegalStateException if nothing has happened
	 */
	public double high() {
		requireMeasured();
		return Math.clamp(centre() + offset(), rate(), 1.0);
	}

	/**
	 * <strong>The two clamps above restore properties this interval has and binary
	 * arithmetic loses, and the size of what they correct is the whole argument for
	 * them.</strong>
	 *
	 * <p>
	 * Algebraically both ends land exactly on 0 and 1 when every observation falls one
	 * way — the offset at {@code p = 1} reduces to {@code z²/(2n·denominator)}, which is
	 * precisely what is left between the centre and one — and the interval always
	 * brackets the observed rate. Binary arithmetic misses each of those by about one
	 * part in 10^16, in either direction: twenty out of twenty comes out at
	 * 1.0000000000000002, which is not a probability at all, and five out of five at
	 * 0.9999999999999999, which is an upper bound sitting below the rate it is meant to
	 * bound.
	 *
	 * <p>
	 * <strong>That is the opposite of clamping the normal approximation</strong>, whose
	 * bound at four out of five is genuinely three points past one. There a clamp would
	 * hide a wrong answer rather than round a right one, which is why the textbook form
	 * is not used here and why {@code CalibrationTests} keeps it around to fail.
	 */
	private double centre() {
		return (rate() + squaredZ() / (2.0 * this.of)) / denominator();
	}

	private double offset() {
		double observed = rate();
		return (Normal.P90_Z / denominator())
				* Math.sqrt(observed * (1.0 - observed) / this.of + squaredZ() / (4.0 * this.of * this.of));
	}

	private double denominator() {
		return 1.0 + squaredZ() / this.of;
	}

	private static double squaredZ() {
		return Normal.P90_Z * Normal.P90_Z;
	}

	private void requireMeasured() {
		if (!measured()) {
			throw new IllegalStateException("Nothing has been counted, so there is no rate to read");
		}
	}

}
