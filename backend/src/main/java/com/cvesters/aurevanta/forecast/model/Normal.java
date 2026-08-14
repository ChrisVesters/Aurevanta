package com.cvesters.aurevanta.forecast.model;

/**
 * The standard normal distribution: the probability below a point, and the point below a
 * probability.
 *
 * <p>
 * <strong>Written here rather than taken from a library, and the surface is why.</strong>
 * The engine needs exactly these two functions — {@link #cdf} to find how much
 * probability a piece of work has already spent, {@link #quantile} to invert it — and
 * ordinary sampling needs neither, since {@code RandomGenerator.nextGaussian()} is in the
 * JDK. Apache Commons Math would supply both and is well tested; it is turned down
 * because {@code commons-math3} has sat in maintenance with its successor unreleased for
 * years, which is not a thing to put underneath the one part of this application that has
 * to still be trustworthy in five years.
 *
 * <p>
 * <strong>What makes that safe is that both functions have exact published values to be
 * tested against.</strong> "Did we get the numerics right" is answerable here rather than
 * a matter of trust, which is the whole argument for hand-rolling them.
 *
 * <p>
 * <strong>The two are deliberately not built on each other.</strong> {@link #quantile}
 * could be refined to machine precision with a Halley step against {@link #cdf}, and is
 * not: that would make {@code cdf(quantile(p)) == p} true by construction, and the test
 * asserting it would stop being evidence. Two rational approximations derived
 * independently, agreeing to a part in a billion, is evidence. A function checking its
 * own homework is not.
 */
public final class Normal {

	/**
	 * The 90th percentile, and so — by symmetry — the negated 10th.
	 *
	 * <p>
	 * Every three-point estimate in this product is fitted through this number, and so is
	 * M3b's team factor. It is written out rather than computed from {@link #quantile} so
	 * that the two disagree loudly if either is wrong; {@code NormalTests} asserts they
	 * agree.
	 */
	public static final double P90_Z = 1.2815515655446004;

	/**
	 * Beyond this the tail is smaller than a double can usefully hold, and
	 * {@code exp(-z²/2)} underflows anyway.
	 */
	private static final double TAIL_LIMIT = 37.0;

	/** Where Hart's rational form gives way to its continued fraction. */
	private static final double HART_SPLIT = 7.07106781186547;

	private static final double SQRT_TWO_PI = 2.506628274631;

	/** Acklam's central region, outside which the fit switches to a tail form. */
	private static final double ACKLAM_SPLIT = 0.02425;

	private Normal() {
	}

	/**
	 * The probability that a standard normal draw falls at or below {@code z}.
	 *
	 * <p>
	 * Hart's rational approximation, accurate to roughly one part in 10^15 across the
	 * whole range — far more than this engine needs, and chosen because the cheaper
	 * series approximations are accurate to about 10^-8 for no meaningful saving.
	 */
	public static double cdf(double z) {
		double magnitude = Math.abs(z);
		double tail;
		if (magnitude > TAIL_LIMIT) {
			tail = 0.0;
		}
		else {
			double density = Math.exp(-magnitude * magnitude / 2.0);
			tail = (magnitude < HART_SPLIT) ? hartRational(magnitude, density) : hartFraction(magnitude, density);
		}
		// Always computed as the *smaller* of the two tails and subtracted only if the
		// larger one was asked for, because that is the half a double holds accurately:
		// 1 - 1e-20 rounds to exactly 1, while 1e-20 keeps every digit it was given.
		return (z > 0.0) ? 1.0 - tail : tail;
	}

	/**
	 * The point below which {@code p} of the distribution lies.
	 *
	 * <p>
	 * Acklam's algorithm, whose relative error is below 1.15 × 10^-9 — comfortably finer
	 * than anything downstream of it can notice, since it is used to place a draw inside
	 * a distribution somebody described with two significant figures.
	 * @throws IllegalArgumentException if {@code p} is not strictly between zero and one,
	 * which has no answer rather than an extreme one
	 */
	public static double quantile(double p) {
		if (!(p > 0.0) || p >= 1.0) {
			throw new IllegalArgumentException("A probability must be strictly between 0 and 1, but was " + p);
		}
		if (p < ACKLAM_SPLIT) {
			return acklamTail(Math.sqrt(-2.0 * Math.log(p)));
		}
		if (p > 1.0 - ACKLAM_SPLIT) {
			// The same tail, reflected — the distribution is symmetric, so there is one
			// approximation rather than two to keep in step.
			return -acklamTail(Math.sqrt(-2.0 * Math.log(1.0 - p)));
		}
		return acklamCentral(p - 0.5);
	}

	private static double hartRational(double magnitude, double density) {
		double numerator = 3.52624965998911e-02 * magnitude + 0.700383064443688;
		numerator = numerator * magnitude + 6.37396220353165;
		numerator = numerator * magnitude + 33.912866078383;
		numerator = numerator * magnitude + 112.079291497871;
		numerator = numerator * magnitude + 221.213596169931;
		numerator = numerator * magnitude + 220.206867912376;
		double denominator = 8.83883476483184e-02 * magnitude + 1.75566716318264;
		denominator = denominator * magnitude + 16.064177579207;
		denominator = denominator * magnitude + 86.7807322029461;
		denominator = denominator * magnitude + 296.564248779674;
		denominator = denominator * magnitude + 637.333633378831;
		denominator = denominator * magnitude + 793.826512519948;
		denominator = denominator * magnitude + 440.413735824752;
		return density * numerator / denominator;
	}

	/** The far tail, where the rational form above loses its accuracy. */
	private static double hartFraction(double magnitude, double density) {
		double fraction = magnitude + 0.65;
		fraction = magnitude + 4.0 / fraction;
		fraction = magnitude + 3.0 / fraction;
		fraction = magnitude + 2.0 / fraction;
		fraction = magnitude + 1.0 / fraction;
		return density / fraction / SQRT_TWO_PI;
	}

	private static double acklamCentral(double fromMiddle) {
		double squared = fromMiddle * fromMiddle;
		double numerator = -3.969683028665376e+01 * squared + 2.209460984245205e+02;
		numerator = numerator * squared - 2.759285104469687e+02;
		numerator = numerator * squared + 1.383577518672690e+02;
		numerator = numerator * squared - 3.066479806614716e+01;
		numerator = numerator * squared + 2.506628277459239e+00;
		double denominator = -5.447609879822406e+01 * squared + 1.615858368580409e+02;
		denominator = denominator * squared - 1.556989798598866e+02;
		denominator = denominator * squared + 6.680131188771972e+01;
		denominator = denominator * squared - 1.328068155288572e+01;
		denominator = denominator * squared + 1.0;
		return numerator * fromMiddle / denominator;
	}

	private static double acklamTail(double depth) {
		double numerator = -7.784894002430293e-03 * depth - 3.223964580411365e-01;
		numerator = numerator * depth - 2.400758277161838e+00;
		numerator = numerator * depth - 2.549732539343734e+00;
		numerator = numerator * depth + 4.374664141464968e+00;
		numerator = numerator * depth + 2.938163982698783e+00;
		double denominator = 7.784695709041462e-03 * depth + 3.224671290700398e-01;
		denominator = denominator * depth + 2.445134137142996e+00;
		denominator = denominator * depth + 3.754408661907416e+00;
		denominator = denominator * depth + 1.0;
		return numerator / denominator;
	}

}
