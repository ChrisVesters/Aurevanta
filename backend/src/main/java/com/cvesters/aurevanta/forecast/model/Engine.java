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
	 * nothing can check. M3b is the first bump, and it keeps this version reachable by
	 * making it the special case where its two parameters are zero.
	 */
	public static final int VERSION = 1;

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
	 * @param sampleCount how many runs to simulate
	 * @param seed stored with the run, and the whole of what makes one reproducible
	 * @throws IllegalArgumentException if the sample count is not a number of runs this
	 * engine will do, or if the plan cannot be scheduled
	 */
	public static Forecast run(List<ItemModel> items, List<Precedence> edges, int capacity, TeamFactor teamFactor,
			int sampleCount, long seed) {
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
		// Everything about the graph is worked out once: it is a property of the plan,
		// and
		// re-deriving it ten thousand times would let the ordering drift between runs.
		Schedule schedule = Schedule.of(edges, typicalEffortHours, underWay, capacity);
		RandomGenerator random = new Random(seed);
		double[] finishes = new double[sampleCount];
		double[] durations = new double[plan.length];
		for (int run = 0; run < sampleCount; run++) {
			// Once, out here, and applied to everything: a stretch drawn per item
			// would average itself away and leave the band exactly where
			// independence left it. It multiplies what each item has *left*, which
			// is what `sample` returns — hours already spent are measured rather
			// than modelled, and a bad quarter that has not happened yet cannot
			// reach back and make them longer.
			double stretch = teamFactor.sample(random);
			for (int at = 0; at < plan.length; at++) {
				durations[at] = stretch * plan[at].sample(random);
			}
			finishes[run] = schedule.finish(durations);
		}
		return summarise(finishes);
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
