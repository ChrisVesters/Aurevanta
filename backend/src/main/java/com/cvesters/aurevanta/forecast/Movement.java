package com.cvesters.aurevanta.forecast;

import java.util.List;

/**
 * The order a date's movement is taken apart in, and the name of that order.
 *
 * <p>
 * <strong>The terms add up, and that is only true because they are computed
 * cumulatively.</strong> "Out 8 days: +5 new scope, +4 re-estimates, −1 progress" claims
 * its parts account for its whole, and the obvious implementation cannot deliver that:
 * re-running the plan with one change at a time and reporting each difference separately
 * gives terms that do not sum, because a simulation is not linear in its inputs. Two
 * changes loading the same bottleneck overlap exactly as M7's two cuts on one chain do.
 *
 * <p>
 * M6 and M7 both met this and both answered <em>do not add them</em>. Here that answer is
 * not available, because the sentence <em>is</em> the feature: an account of a movement
 * that does not account for the movement is not an account. So each step is applied on
 * top of every earlier one and its term is the difference it made from there — and the
 * final state is the newer run itself, which is what makes the sum exact rather than
 * approximate.
 *
 * <p>
 * <strong>The order therefore decides the attribution, which is why it has a
 * name.</strong> {@code Schedule.PRIORITY_RULE}'s argument: two defensible orders split
 * the same eight days differently, so a decomposition read under one must never be
 * silently compared with one read under another. Reordering {@link #ORDER} is not a
 * refactor — a reader told that scope cost them five days and estimates four will act on
 * it, and swapping those two steps moves days between the lines.
 */
public final class Movement {

	/**
	 * What the order below is called, published with every decomposition read under it.
	 */
	public static final String RULE = "progress_first";

	/**
	 * The order, and it is the whole of the rule above.
	 *
	 * <p>
	 * Settled things first, then opinions, then new work, then what somebody changed
	 * about the question, and last the part nobody decided.
	 */
	public static final List<Step> ORDER = List.of(Step.SAMPLING, Step.PROGRESS, Step.ESTIMATES, Step.SCOPE,
			Step.ASSUMPTIONS, Step.CALENDAR, Step.STARTS_ON);

	private Movement() {
	}

	/** One thing that can have moved a date between two forecasts of one plan. */
	public enum Step {

		/**
		 * <strong>Two runs never share a seed, so re-running is itself a
		 * difference.</strong> The plan for this milestone did not name this step and the
		 * arithmetic requires it: the last state has to <em>be</em> the newer run, seed
		 * included, or the terms sum to something that is not the distance between the
		 * two stored dates. It is measured against the older run's own stored result and
		 * is almost always nothing — a date is a whole day and the sampler moves the
		 * hours by about a fifth of one at ten thousand samples — but almost always is
		 * not by construction, and this is what makes it so.
		 */
		SAMPLING,

		/**
		 * Work that finished, or hours spent on work still running. First because it is
		 * not a decision anybody took between the two runs: it is the baseline the rest
		 * is measured against rather than a change competing with them.
		 */
		PROGRESS,

		/** A second opinion about work that was already listed. */
		ESTIMATES,

		/**
		 * Work that was not there before, or is no longer there — and the arrows with it.
		 */
		SCOPE,

		/**
		 * Capacity, the bad-week assumption, the growth range: the question, not the
		 * plan.
		 */
		ASSUMPTIONS,

		/**
		 * The working day or the calendar rule. It changes no hours at all and can still
		 * move a date by a week, which is why it is a step of its own rather than folded
		 * into the assumptions above — a reader who has just adjusted a working day
		 * should be told that is what they did.
		 */
		CALENDAR,

		/**
		 * The day work was said to begin. Last, because it is the one term that is
		 * nobody's doing and shifts everything already counted: a plan started a month
		 * later finishes a month later.
		 */
		STARTS_ON

	}

}
