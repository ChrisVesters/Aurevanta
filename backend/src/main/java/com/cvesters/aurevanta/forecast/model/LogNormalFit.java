package com.cvesters.aurevanta.forecast.model;

/**
 * A three-point estimate, turned into the distribution a forecast can sample.
 *
 * <p>
 * <strong>Log-normal, fitted from the two ends only.</strong> Durations are positive and
 * right-skewed, and a log-normal does not pretend a worst case exists — which is the
 * property that matters, because a bounded fit is a claim nobody made. Two parameters
 * come from two points:
 *
 * <pre>
 * sigma = (ln P90 - ln P10) / (2 * z90)
 * mu    = (ln P10 + ln P90) / 2
 * </pre>
 *
 * <p>
 * <strong>The stated P50 is never an input, and that is the decision inside the
 * decision.</strong> Three points over-determine two parameters, so something has to
 * give: fitting all three by least squares, or honouring all three with a PERT-beta, both
 * bury the disagreement inside the fit. Keeping the two ends and reporting the
 * discrepancy — {@link #consistency} — turns it into what it actually is, a signal that
 * somebody's three numbers argue with each other. The tails are also where the
 * information is: P10 and P90 are what elicitation works hardest to get honest, and the
 * P50 is the number people answer fastest and think about least.
 *
 * <p>
 * <strong>Doubles rather than {@code BigDecimal}, deliberately.</strong> The column
 * stores two decimal places because that is a quantity somebody typed; everything from
 * here on is arithmetic over distributions, where decimal exactness buys nothing and
 * costs the ability to reason about the result. Converting is the service layer's job,
 * and it is the boundary this whole package sits behind: no Spring, no JPA, no I/O, and
 * nothing here knows what a work item is.
 */
public record LogNormalFit(double mu, double sigma) {

	/**
	 * Fits the two ends of an estimate.
	 *
	 * <p>
	 * <strong>Equal ends are not a degenerate case to refuse.</strong> They make
	 * {@code sigma} zero, which is a point mass — somebody saying they are certain — and
	 * every draw returns the same number. The plan schema accepts three identical values,
	 * so this must too, and nothing here divides by {@code sigma} in order that it can.
	 *
	 * <p>
	 * The two refusals below cannot be reached through the API: {@code @Positive} and
	 * {@code estimate_out_of_order} between them guarantee {@code 0 < p10 <= p90} before
	 * any request gets this far. They are here anyway because this is a pure function
	 * that a test calls directly, so the branch is coverable — which is exactly the
	 * distinction {@code ProjectService.lockForGraphChange} makes in the other direction,
	 * where the refusal was removed for being unreachable by anything at all. The
	 * alternative is worse than an unused branch: {@code Math.log} of a negative is a
	 * silent {@code NaN} that would travel all the way to a percentile before anybody
	 * noticed.
	 * @throws IllegalArgumentException if the low end is not above zero, or the two are
	 * the wrong way round
	 */
	public static LogNormalFit from(double p10, double p90) {
		if (!(p10 > 0.0)) {
			throw new IllegalArgumentException("An estimate must be greater than zero, but the low end was " + p10);
		}
		if (!(p90 >= p10)) {
			throw new IllegalArgumentException("An estimate must ascend, but " + p90 + " is below " + p10);
		}
		double low = Math.log(p10);
		double high = Math.log(p90);
		return new LogNormalFit((low + high) / 2.0, (high - low) / (2.0 * Normal.P90_Z));
	}

	/**
	 * The outcome this fit puts {@code z} standard deviations from its middle, measured
	 * in the logarithm — so {@code at(0)} is the median and every draw from this
	 * distribution is this function of a standard normal number.
	 *
	 * <p>
	 * Both ways of sampling go through here, which is the point of it existing: an
	 * ordinary draw passes a Gaussian, and a draw conditioned on work already done passes
	 * a point picked out of the surviving tail. Written once so the two cannot drift into
	 * disagreeing about what this distribution is.
	 *
	 * <p>
	 * A {@code sigma} of zero collapses this to the median whatever {@code z} is, which
	 * is what makes a point mass need no special case anywhere upstream.
	 */
	public double at(double z) {
		return Math.exp(this.mu + this.sigma * z);
	}

	/**
	 * The middle this fit implies, which is the geometric mean of the two ends — and so
	 * not, in general, the number the estimator wrote in the middle box.
	 */
	public double median() {
		return at(0.0);
	}

	/**
	 * The average outcome, which sits <em>above</em> the median and further above it the
	 * wider the range. That gap is the whole of why percentiles do not add, and why
	 * summing P50s understates.
	 */
	public double mean() {
		return Math.exp(this.mu + this.sigma * this.sigma / 2.0);
	}

	/**
	 * The spread, in the units of the estimate squared.
	 *
	 * <p>
	 * This is the number the closed-form oracle is built on: variances of independent
	 * draws add exactly, whatever shape they are, so a chain of tasks has an analytic
	 * variance for the sampler to be checked against.
	 *
	 * <p>
	 * {@code expm1} rather than {@code exp(x) - 1} — for a narrow range the two differ by
	 * everything, since {@code exp} of a small number rounds to 1 and the subtraction
	 * then yields zero. It also makes a point mass come out at exactly zero rather than
	 * near it.
	 */
	public double variance() {
		double spread = this.sigma * this.sigma;
		return Math.expm1(spread) * Math.exp(2.0 * this.mu + spread);
	}

	/**
	 * How far the stated middle is from the one these two ends imply, as a ratio: 1.0 is
	 * agreement, above means the estimator's middle is later than their own range
	 * suggests, and below means earlier.
	 *
	 * <p>
	 * <strong>Reported, never acted on.</strong> A discrepancy is not a fault to refuse —
	 * the plan schema accepts any ascending three points on purpose — it is evidence that
	 * the three numbers were not thought about together, which is the first thing
	 * elicitation's elicitation work will want to see.
	 */
	public double consistency(double statedP50) {
		return statedP50 / median();
	}

}
