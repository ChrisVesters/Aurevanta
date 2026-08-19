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
public final class Contributions implements RunObserver {

	/**
	 * The two sources a forecast has that are not items, and the reason a ranking of
	 * items alone would be dishonest: either can dominate, and when one does the true
	 * answer to "which task should I spike" is "none of them".
	 *
	 * <p>
	 * They sit after the plan's own items so that an item's index is its position in the
	 * plan, which is what lets the identifiers come back off the stored snapshot.
	 */
	private static final int BESIDE_THE_ITEMS = 2;

	private final double[] mean;

	/**
	 * Welford's the plan schema per source: the sum of squared deviations from the
	 * running mean.
	 */
	private final double[] spread;

	/** The co-moment of each source with the outcome. */
	private final double[] together;

	/**
	 * One row, reused, for the run-shaped
	 * {@link #observed(double[], int, double, double, double)} below: the plan's
	 * durations with the two sources beside them copied on the end. Allocated by every
	 * accumulator including the ones built generically, which costs a handful of doubles
	 * and keeps one constructor rather than two.
	 */
	private final double[] watched;

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
		this.watched = new double[sources];
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
	 * estimated, which the simulation engine keeps in the graph as a zero-effort node;
	 * work already finished, which has nothing left; and an estimate of three identical
	 * numbers, which the plan schema accepts on purpose because it is somebody saying
	 * they are certain. Their correlation is {@code 0/0}, and a {@code NaN} would sort
	 * unpredictably through a ranking and fail to serialise at all — so the answer is
	 * zero, which is also true: a number that never moves cannot be what the finish moved
	 * with.
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

	// Watching a forecast ------------------------------------------------------

	/**
	 * An accumulator shaped for a plan: one source per item, and two more for the things
	 * that move a forecast without being items.
	 * @param items how many pieces of work the plan wrote down
	 */
	public static Contributions forRun(int items) {
		return new Contributions(items + BESIDE_THE_ITEMS);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * The plan's durations are read out of the engine's own array — which may be longer,
	 * because a run that discovered work schedules it in the same array — and the two
	 * sources beside them are added on the end. Nothing is allocated: the row handed to
	 * the accumulator is the same one every run.
	 */
	@Override
	public void observed(double[] durations, int items, double discoveredHours, double stretch, double completion) {
		if (items + BESIDE_THE_ITEMS != this.watched.length) {
			throw new IllegalArgumentException(
					"This forecast has " + (this.watched.length - BESIDE_THE_ITEMS) + " items and was shown " + items);
		}
		System.arraycopy(durations, 0, this.watched, 0, items);
		this.watched[items] = discoveredHours;
		this.watched[items + 1] = stretch;
		observed(this.watched, completion);
	}

	/** What the plan's own work accounted for, by its position in the plan. */
	public Contribution ofItem(int item) {
		return of(item);
	}

	/**
	 * What the work nobody had listed accounted for, taken together.
	 *
	 * <p>
	 * Together rather than one by one, because discovered work is different work in every
	 * run and there is no thing to rank. Its total is a series like any other, and on a
	 * plan expected to grow it is frequently the largest of them —
	 * {@code product-concept.md} is explicit that scope growth is usually the bigger of
	 * the two uncertainty sources.
	 */
	public Contribution ofDiscoveredWork() {
		return of(this.watched.length - BESIDE_THE_ITEMS);
	}

	/**
	 * What the one multiplier every item in a run shared accounted for.
	 *
	 * <p>
	 * The most useful row in the report when it is the largest: it says that no estimate
	 * on the list is the problem, and that the plan's spread is a claim somebody made
	 * about how bad a bad quarter is.
	 */
	public Contribution ofTeamFactor() {
		return of(this.watched.length - 1);
	}

}
