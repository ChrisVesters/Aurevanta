package com.cvesters.aurevanta.forecast.model;

import java.util.List;

/**
 * What several people's ranges on one piece of work say together: the distribution the
 * engine samples that work from, read as percentiles.
 *
 * <p>
 * <strong>It is the same distribution {@link ItemModel#sample} draws from, not an
 * approximation of it.</strong> The engine picks one estimator per item per run, each
 * equally likely, and draws from that person's fit — so the work's duration is an
 * equal-weight mixture, and its cumulative probability at any number of hours is the mean
 * of the estimators' own. That is exact, so the percentiles are found by searching for
 * where it reaches 0.1, 0.5 and 0.9 rather than by simulating: a simulation would answer
 * the same question with sampling noise in it, and need a seed to give the same answer
 * twice.
 *
 * <p>
 * <strong>It is never an average, and never a fit of its own.</strong> Averaging the
 * three numbers turns two people who disagree into a narrow band neither of them gave —
 * {@code design/simulation-engine.md} decision 3. Refitting one log-normal through the
 * mixture's ends would lose its shape: two clusters of opinion become one hump, and the
 * tails move. So this is read and shown, and nothing feeds it back to the engine.
 *
 * <p>
 * Like {@link LogNormalFit} it uses only each estimate's two ends, which is what the fit
 * reads; a stated middle is the one number the engine does not use.
 */
public final class EstimatorMixture {

	/**
	 * Halvings of the search window, in logarithms of hours. The window starts no wider
	 * than the log-distance between two estimators' readings of one percentile, and a
	 * {@code numeric(12, 2)} column bounds that by about thirty; sixty-four halvings put
	 * it below a double's resolution, so the answer is as exact as the arithmetic allows
	 * and a fixed count keeps it identical on every call.
	 */
	private static final int HALVINGS = 64;

	private final List<LogNormalFit> fits;

	private EstimatorMixture(List<LogNormalFit> fits) {
		this.fits = fits;
	}

	/**
	 * @param fits one per estimator, each weighing the same — which is how the engine
	 * picks between them
	 */
	public static EstimatorMixture of(List<LogNormalFit> fits) {
		if (fits.isEmpty()) {
			throw new IllegalArgumentException("A mixture needs at least one estimate");
		}
		return new EstimatorMixture(List.copyOf(fits));
	}

	/** How likely the work is to take no more than this many hours. */
	public double cdf(double hours) {
		double total = 0.0;
		for (LogNormalFit fit : this.fits) {
			total += cdf(fit, hours);
		}
		return total / this.fits.size();
	}

	/**
	 * The number of hours the work comes in under with probability {@code p}.
	 *
	 * <p>
	 * The answer lies between the lowest and highest of the estimators' own readings of
	 * that percentile: at the lowest, nobody's chance has reached {@code p} yet, and at
	 * the highest, everybody's has. With one estimator, or several who agree, those are
	 * the same number and it is the answer.
	 */
	public double quantile(double p) {
		double z = Normal.quantile(p);
		double low = Double.POSITIVE_INFINITY;
		double high = 0.0;
		for (LogNormalFit fit : this.fits) {
			double reading = fit.at(z);
			low = Math.min(low, reading);
			high = Math.max(high, reading);
		}
		double logLow = Math.log(low);
		double logHigh = Math.log(high);
		for (int step = 0; step < HALVINGS; step++) {
			double middle = (logLow + logHigh) / 2.0;
			if (cdf(Math.exp(middle)) < p) {
				logLow = middle;
			}
			else {
				logHigh = middle;
			}
		}
		return Math.exp((logLow + logHigh) / 2.0);
	}

	/**
	 * One estimator's chance of finishing within this many hours. A range of no width —
	 * three identical numbers, which the schema accepts as somebody being certain — is a
	 * step at its value rather than a division by zero.
	 */
	private static double cdf(LogNormalFit fit, double hours) {
		if (fit.sigma() == 0.0) {
			return (hours >= fit.median()) ? 1.0 : 0.0;
		}
		return Normal.cdf((Math.log(hours) - fit.mu()) / fit.sigma());
	}

}
