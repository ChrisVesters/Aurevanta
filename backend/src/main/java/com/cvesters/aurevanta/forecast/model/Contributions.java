package com.cvesters.aurevanta.forecast.model;

/**
 * What the spread of a forecast is made of, accumulated one run at a time.
 *
 * <p>
 * <strong>Nothing is kept but running totals.</strong> Ten thousand runs over the five
 * hundred items a plan may hold is five million numbers, and keeping them is what
 * `roadmap.md` rejected in advance — so this holds three doubles per source and two for
 * the outcome, updated once per run and never grown. That is also why the answer can be
 * had by replaying a stored forecast rather than by having stored anything: a replay
 * produces the same draws, and this turns them into a ranking as they go past.
 *
 * <p>
 * <strong>Welford's co-moments rather than sums of squares, and that was
 * measured.</strong> The formula everybody writes first accumulates {@code Σx},
 * {@code Σx²} and {@code Σxy} and subtracts {@code n·Σx² − (Σx)²} at the end. Those two
 * terms are nearly equal and enormous once a plan is long, so the difference is noise: on
 * a plan of a million hours with a tight item it is wrong in the third decimal — enough
 * to reorder a ranking whose whole purpose is the order — and on a plan of a billion the
 * subtraction goes negative and {@code sqrt} returns {@code NaN}, which is not valid
 * JSON. A million hours is not contrived: {@code @Digits(integer = 10, fraction = 2)}
 * lets one estimate be ten billion. This is {@link LogNormalFit#variance}'s {@code expm1}
 * problem in a second place.
 *
 * <p>
 * The update below is shift- and scale-invariant in the way a correlation is meant to be:
 * adding a billion to every number changes nothing, and so does multiplying by sixty.
 * {@code ContributionsTests} asserts both, because they are what the naive formula loses.
 */
public final class Contributions {

	private final double[] mean;

	/** Welford's M2 per source: the sum of squared deviations from the running mean. */
	private final double[] spread;

	/** The co-moment of each source with the outcome. */
	private final double[] together;

	private double meanOutcome;

	private double spreadOutcome;

	private int runs;

	/**
	 * @param sources how many things could move the finish — every item in the plan, and
	 * whatever else a run draws
	 * @throws IllegalArgumentException if that is not a number of things
	 */
	public Contributions(int sources) {
		if (sources < 0) {
			throw new IllegalArgumentException("A forecast cannot have " + sources + " sources of spread");
		}
		this.mean = new double[sources];
		this.spread = new double[sources];
		this.together = new double[sources];
	}

	/**
	 * One run: what each source drew, and when the plan finished because of it.
	 *
	 * <p>
	 * The outcome's mean is advanced before any source uses it, because Welford's
	 * co-moment update pairs the <em>old</em> deviation of one series with the
	 * <em>new</em> deviation of the other. Doing it the other way round is a subtle bias
	 * that no small test would notice.
	 * @throws IllegalArgumentException if there is not a value for every source
	 */
	public void observed(double[] values, double outcome) {
		if (values.length != this.mean.length) {
			throw new IllegalArgumentException(
					"This forecast has " + this.mean.length + " sources and was given " + values.length + " values");
		}
		this.runs++;
		double outcomeWas = outcome - this.meanOutcome;
		this.meanOutcome += outcomeWas / this.runs;
		this.spreadOutcome += outcomeWas * (outcome - this.meanOutcome);
		for (int source = 0; source < values.length; source++) {
			double was = values[source] - this.mean[source];
			this.mean[source] += was / this.runs;
			this.spread[source] += was * (values[source] - this.mean[source]);
			this.together[source] += was * (outcome - this.meanOutcome);
		}
	}

	/**
	 * How much the finish moved with one source.
	 *
	 * <p>
	 * <strong>A source that never varied contributes exactly nothing, and that is the
	 * ordinary case.</strong> Three of them arrive in every real plan: an item nobody
	 * estimated, which M3a keeps in the graph as a zero-effort node; work already
	 * finished, which has nothing left; and an estimate of three identical numbers, which
	 * M2 accepts on purpose because it is somebody saying they are certain. Their
	 * correlation is {@code 0/0}, and a {@code NaN} would sort unpredictably through a
	 * ranking and fail to serialise at all — so the answer is zero, which is also true: a
	 * number that never moves cannot be what the finish moved with.
	 *
	 * <p>
	 * The same guard covers a plan that finished at the same moment in every run. There
	 * is no spread to attribute, so nothing is attributed any of it — the choice
	 * {@link Histogram} already makes when it puts everything in one bucket rather than
	 * dividing by a width of zero. Written as {@code !(x > 0)} so that a {@code NaN}
	 * arriving from anywhere upstream takes the same exit rather than escaping through a
	 * comparison that is false either way.
	 */
	public Contribution of(int source) {
		double both = this.spread[source] * this.spreadOutcome;
		if (!(both > 0.0)) {
			return Contribution.NONE;
		}
		return new Contribution(this.together[source] / Math.sqrt(both));
	}

	/** How many runs have been seen, which is what makes an empty accumulator legible. */
	public int runs() {
		return this.runs;
	}

}
