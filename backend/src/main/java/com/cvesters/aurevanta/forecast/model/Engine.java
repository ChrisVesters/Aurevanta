package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import com.cvesters.aurevanta.item.WorkItemStatus;

/**
 * Fit, sample, schedule, repeat: the whole of what this product does, and the reason
 * everything before it was built.
 *
 * <p>
 * Each run draws one duration per item — from whichever estimator it picked, conditioned
 * on whatever has already been spent — and schedules the graph to find out when the plan
 * would have finished. Ten thousand of those make a distribution, and the percentiles are
 * read off it. <strong>Percentiles do not add</strong>, which is the sentence this
 * product exists for: the P90 of a plan is not the sum of its items' P90s, because that
 * assumes everything goes wrong at once.
 *
 * <p>
 * <strong>How anybody knows this is right.</strong> A schedule at capacity one is a sum
 * of independent draws, and a sum has an exactly known mean and variance whatever shapes
 * went into it. So the sampler is not trusted, it is measured — {@code EngineTests}
 * asserts it converges on arithmetic that exists outside this codebase, including a P90
 * of 811 hours that `roadmap.md` measured before any of this was written.
 *
 * <p>
 * <strong>{@link Random} rather than a faster generator, and that is a decision.</strong>
 * A stored run keeps its seed so it can be replayed years later, which is worthless if
 * the numbers move underneath it. {@code java.util.Random} is the only generator in the
 * JDK whose algorithms are written into its contract rather than merely into its
 * implementation, so a seed still means the same thing after a JDK upgrade.
 * {@code SplittableRandom} is quicker and its Gaussian comes from a default method
 * nothing promises to keep.
 */
public final class Engine {

	/**
	 * Bumped whenever the model changes, and stored on every run.
	 *
	 * <p>
	 * A forecast can be replayed from its seed only if the engine that made it still
	 * behaves the same way, so a run that does not say which engine made it is a run
	 * nothing can check.
	 *
	 * <p>
	 * <strong>Version 2 contains version 1 rather than replacing it.</strong> M3a is this
	 * engine with {@link TeamFactor#NONE} and {@link ScopeGrowth#NONE} — not
	 * approximately but draw for draw, because a parameter that changes no number also
	 * takes no draw from the generator. So replaying a version 1 run means running
	 * version 2 with the parameters version 1 implied, and there is no second code path
	 * to be kept in step with this one.
	 *
	 * <p>
	 * <strong>The rule that buys, for whoever bumps this next.</strong> Either the new
	 * engine contains the old one as a setting of its parameters, or every run made
	 * before the bump becomes a record that can be read and never replayed. A change that
	 * cannot be reduced to a parameter — a different fit, a different scheduler — does
	 * not get to pretend otherwise: it bumps this, old runs become read-only history, and
	 * M10 has to be told, because comparing runs across an incomparable bump is how a
	 * tool reports a date sliding when nothing moved.
	 */
	public static final int VERSION = 2;

	/**
	 * Sampling error at ten thousand runs is about ±0.77%, against 2% to 5% for the
	 * closed form this product rejected — an order of magnitude better, for a tenth of
	 * the cost of a hundred thousand. It is also what keeps a forecast inside a request.
	 */
	public static final int DEFAULT_SAMPLE_COUNT = 10_000;

	/**
	 * A bound on absurdity rather than a promise of speed: at the five hundred items a
	 * plan may hold, this many runs is seconds rather than milliseconds. What actually
	 * stops one member tying up a server is a limit on how many forecasts run at once,
	 * and that is not built.
	 */
	public static final int MAX_SAMPLE_COUNT = 100_000;

	/** Enough to draw a curve from, and small enough to store beside the run. */
	private static final int BUCKETS = 100;

	/** What a run that discovered nothing hands the scheduler. */
	private static final int[] NOTHING_FOUND = new int[0];

	private Engine() {
	}

	/**
	 * Forecasts a plan.
	 * @param items every piece of work in the plan, including the ones nobody estimated —
	 * they weigh nothing and still hold their place in the graph
	 * @param edges what has to finish before what, by position in {@code items}
	 * @param capacity how many items may be under way at once. There is no default
	 * anywhere: it moves the answer by more than half, so somebody has to say it.
	 * @param teamFactor the common cause every item in a run shares, or
	 * {@link TeamFactor#NONE} for the independence this product does not believe in
	 * @param scopeGrowth how much work nobody has written down yet, or
	 * {@link ScopeGrowth#NONE} to forecast the plan exactly as listed
	 * @param sampleCount how many runs to simulate
	 * @param seed stored with the run, and the whole of what makes one reproducible
	 * @throws IllegalArgumentException if the sample count is not a number of runs this
	 * engine will do, if the plan cannot be scheduled, or if a plan expected to grow
	 * holds no estimate for the new work to look like
	 */
	public static Forecast run(List<ItemModel> items, List<Precedence> edges, int capacity, TeamFactor teamFactor,
			ScopeGrowth scopeGrowth, int sampleCount, long seed) {
		if (sampleCount < 1 || sampleCount > MAX_SAMPLE_COUNT) {
			throw new IllegalArgumentException(
					"A forecast runs between 1 and " + MAX_SAMPLE_COUNT + " times, not " + sampleCount);
		}
		ItemModel[] plan = items.toArray(new ItemModel[0]);
		double[] typicalEffortHours = new double[plan.length];
		boolean[] underWay = new boolean[plan.length];
		for (int at = 0; at < plan.length; at++) {
			typicalEffortHours[at] = plan[at].typicalEffortHours();
			underWay[at] = plan[at].status() == WorkItemStatus.IN_PROGRESS;
		}
		ItemModel[] reference = referenceClass(plan);
		if (!ScopeGrowth.NONE.equals(scopeGrowth) && reference.length == 0) {
			// Unreachable through the API, where `nothing_to_forecast` has already
			// refused
			// a plan with no estimate in it — a refusal doing load-bearing work two
			// milestones away from where it was written, and worth knowing about before
			// somebody relaxes it. Reachable from a test, which is why it is stated.
			throw new IllegalArgumentException(
					"A plan expected to grow needs an estimate for the new work to resemble");
		}
		// Everything about the graph is worked out once: it is a property of the plan,
		// and
		// re-deriving it ten thousand times would let the ordering drift between runs.
		Schedule schedule = Schedule.of(edges, typicalEffortHours, underWay, capacity);
		RandomGenerator random = new Random(seed);
		double[] finishes = new double[sampleCount];
		double[] durations = new double[plan.length];
		int[] parentOf = NOTHING_FOUND;
		for (int run = 0; run < sampleCount; run++) {
			// Once, out here, and applied to everything: a stretch drawn per item
			// would average itself away and leave the band exactly where
			// independence left it. It multiplies what each item has *left*, which
			// is what `sample` returns — hours already spent are measured rather
			// than modelled, and a bad quarter that has not happened yet cannot
			// reach back and make them longer.
			double stretch = teamFactor.sample(random);
			int found = scopeGrowth.sample(plan.length, random);
			if (found > parentOf.length) {
				// Kept rather than sized per run: a later run that discovers less work
				// reads fewer entries of the same arrays, which is what `finish` allowing
				// a longer durations array is for.
				parentOf = new int[found];
				durations = Arrays.copyOf(durations, plan.length + found);
			}
			for (int at = 0; at < found; at++) {
				// New work costs what this plan's work costs, and lands behind a piece of
				// it chosen with no regard for which — a weaker claim than saying it
				// lands
				// on the critical path, or at the end, or nowhere.
				durations[plan.length + at] = stretch
						* reference[random.nextInt(reference.length)].sampleAsNewWork(random);
				parentOf[at] = random.nextInt(plan.length);
			}
			for (int at = 0; at < plan.length; at++) {
				durations[at] = stretch * plan[at].sample(random);
			}
			finishes[run] = schedule.finish(durations, parentOf, found);
		}
		return summarise(finishes);
	}

	/**
	 * The work this plan can describe new work by: everything somebody estimated,
	 * regardless of how far along it is.
	 *
	 * <p>
	 * Finished work is included on purpose. What is wanted is an estimate of a piece of
	 * this team's work, and an item being done says nothing about how big the next one
	 * is.
	 */
	private static ItemModel[] referenceClass(ItemModel[] plan) {
		int found = 0;
		ItemModel[] estimated = new ItemModel[plan.length];
		for (ItemModel item : plan) {
			if (!item.estimates().isEmpty()) {
				estimated[found++] = item;
			}
		}
		return Arrays.copyOf(estimated, found);
	}

	private static Forecast summarise(double[] finishes) {
		double total = 0.0;
		for (double finish : finishes) {
			total += finish;
		}
		double mean = total / finishes.length;
		double squares = 0.0;
		for (double finish : finishes) {
			squares += (finish - mean) * (finish - mean);
		}
		// Divided by the count rather than one less than it: at ten thousand runs the
		// difference is a hundredth of a percent, far under the sampling error either
		// way,
		// and it means a single run answers with zero instead of a NaN.
		double deviation = Math.sqrt(squares / finishes.length);
		double[] sorted = finishes.clone();
		Arrays.sort(sorted);
		return new Forecast(mean, deviation, at(sorted, 0.10), at(sorted, 0.50), at(sorted, 0.80), at(sorted, 0.90),
				at(sorted, 0.95), histogram(sorted));
	}

	/**
	 * The value below which that share of the runs finished.
	 *
	 * <p>
	 * Nearest rank, with no interpolation between neighbours. Ten thousand runs put the
	 * P90 between two order statistics that differ by far less than the sampling error
	 * separating either of them from the truth, so interpolating would be precision
	 * invented on top of a number that does not have it.
	 */
	private static double at(double[] sorted, double share) {
		int index = (int) Math.ceil(share * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
	}

	private static Histogram histogram(double[] sorted) {
		double from = sorted[0];
		double to = sorted[sorted.length - 1];
		double width = (to - from) / BUCKETS;
		int[] counts = new int[BUCKETS];
		for (double finish : sorted) {
			// A plan whose every run finishes at the same moment has no width to divide
			// by, and one bucket holding everything is the honest picture of it.
			int bucket = (width > 0.0) ? Math.min((int) ((finish - from) / width), BUCKETS - 1) : 0;
			counts[bucket]++;
		}
		List<Integer> shape = new ArrayList<>(BUCKETS);
		for (int count : counts) {
			shape.add(count);
		}
		return new Histogram(from, to, shape);
	}

}
