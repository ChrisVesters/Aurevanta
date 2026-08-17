package com.cvesters.aurevanta.forecast;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why the search for a set of cuts stopped.
 *
 * <p>
 * <strong>Three endings, and telling them apart is the point.</strong> A list that
 * reaches the bar and a list that is the best anybody could find are different answers,
 * and a search that ran out of time is a third — one where the answer is "keep looking"
 * rather than "there is nothing more". A search that stopped early and did not say so is
 * a search reporting the best thing it happened to look at, which is the failure mode of
 * every heuristic that reports a result rather than a result and a reason.
 */
public enum CutSearchEnding {

	/** The bar was cleared, and everything before this in the list is what it took. */
	MET("met"),

	/**
	 * Everything on offer was cut and the bar is still out of reach. The answer is the
	 * best that could be done with what was named — a different candidate set may do
	 * better, and that is a question about what else is negotiable.
	 */
	NOTHING_LEFT("nothing_left"),

	/**
	 * The search ran out of the simulations it is allowed, with candidates still
	 * unweighed.
	 *
	 * <p>
	 * Each step of the search costs a whole run of the plan for every candidate still in
	 * play, so the work grows with the square of what is offered. Said out loud, because
	 * the honest reading is "this is as far as it looked", not "this is as far as it
	 * goes".
	 */
	BUDGET_SPENT("budget_spent");

	private final String code;

	CutSearchEnding(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return this.code;
	}

}
