package com.cvesters.aurevanta.forecast.model;

/**
 * Something told what one run of a forecast drew, after it has drawn it.
 *
 * <p>
 * <strong>This is how a forecast can be watched without being changed.</strong> the
 * contribution ranking needs a duration per item per run to work out what the plan's
 * spread is made of, and storing those is five million numbers per forecast — so the
 * engine hands them over as they go past instead, and whoever is listening reduces them
 * to running totals. That is the whole reason a stored run can be explained years later
 * from nothing but its seed.
 *
 * <p>
 * <strong>Called after everything that draws, and it draws nothing itself.</strong> the
 * common-cause model's sharpest rule is that a parameter set to none must consume no
 * randomness, because a draw that changed no number would still advance the generator and
 * silently unreplay every forecast stored before it. An observer is not random and cannot
 * break that — but it is held to the same discipline for the same reason, and
 * {@code EngineTests} asserts that attaching one changes no number at all.
 * {@link Engine#VERSION} does not move for this.
 *
 * <p>
 * <strong>The array is the engine's and is reused.</strong> It is longer than the plan
 * when a run discovered work, and it is overwritten by the next run — so an observer that
 * keeps it keeps whatever the last run put in it. Read it, do not hold it.
 */
@FunctionalInterface
public interface RunObserver {

	/**
	 * Nobody is watching, which is what every forecast that is not being explained uses.
	 *
	 * <p>
	 * A no-op rather than a null check in the loop, following {@link TeamFactor#NONE}:
	 * the absence of a thing is a thing, and the engine reads the same either way.
	 */
	RunObserver NONE = (durations, items, discoveredHours, stretch, completion) -> {
	};

	/**
	 * One run, as it happened.
	 * @param durations what each piece of work turned out to take. The first
	 * {@code items} entries are the plan's, in its own order; anything after them is work
	 * this run discovered, and the array may be longer still.
	 * @param items how many of those entries the plan wrote down, which is the only part
	 * an observer can name across runs — discovered work is different work every time.
	 * @param discoveredHours everything this run found that nobody had listed, added up.
	 * A plan forecast without scope growth reports zero here in every run, which is a
	 * source that never varies rather than a source that is missing.
	 * @param stretch the one multiplier this run applied to everything, or exactly 1
	 * where no common cause was modelled
	 * @param completion when the plan finished, which is what all of the above is being
	 * measured against
	 */
	void observed(double[] durations, int items, double discoveredHours, double stretch, double completion);

}
