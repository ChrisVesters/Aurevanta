package com.cvesters.aurevanta.forecast.model;

/**
 * How often a plan came in within a budget of hours.
 *
 * <p>
 * <strong>The whole of an inverse query's answer, and it needed no engine
 * change.</strong> "Can we hit 1 November at 85%?" is a question about a date, which M4's
 * calendar turns into a number of hours; from there it is a count of the runs that came
 * in under it. M6 built the seam that makes that free — {@link RunObserver} is told each
 * run's completion as it goes past — so nothing here samples anything, stores anything,
 * or knows what a date is.
 *
 * <p>
 * <strong>Counting rather than reading a percentile, and the difference matters at the
 * ends.</strong> A forecast keeps five percentiles, and answering from those would mean
 * interpolating between two of them — inventing precision the sampling does not have,
 * which is the same reason {@code Engine.at} takes the nearest rank rather than
 * interpolating. Counting is exact about the only thing being asked: how many of these
 * runs beat the date.
 */
public final class ConfidenceBy implements RunObserver {

	private final double budgetHours;

	private int within;

	private int runs;

	/**
	 * @param budgetHours everything a plan may take and still count as having made it
	 * @throws IllegalArgumentException if that is less than no work at all
	 */
	public ConfidenceBy(double budgetHours) {
		if (!(budgetHours >= 0.0)) {
			throw new IllegalArgumentException(
					"A plan cannot be given less than no time, but was given " + budgetHours);
		}
		this.budgetHours = budgetHours;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * Everything but the completion is ignored, and deliberately: this asks whether the
	 * plan made it, not what made it late. The second question is M6's and has its own
	 * observer.
	 */
	@Override
	public void observed(double[] durations, int items, double discoveredHours, double stretch, double completion) {
		this.runs++;
		if (completion <= this.budgetHours) {
			this.within++;
		}
	}

	/**
	 * The share of runs that came in within the budget, from 0 to 1.
	 *
	 * <p>
	 * <strong>A share of no runs is nothing, and {@link #runs()} is what tells them
	 * apart.</strong> The engine refuses a sample count below one, so an accumulator with
	 * no runs has not been shown a forecast at all rather than shown a bad one — the same
	 * distinction {@code Contributions} draws, answered the same way.
	 */
	public double share() {
		return (this.runs == 0) ? 0.0 : (double) this.within / this.runs;
	}

	/** How many runs have been seen, which is what makes an empty accumulator legible. */
	public int runs() {
		return this.runs;
	}

}
