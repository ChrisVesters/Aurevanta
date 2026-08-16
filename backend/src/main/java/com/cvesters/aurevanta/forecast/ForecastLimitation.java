package com.cvesters.aurevanta.forecast;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a forecast did not do, said in codes rather than in prose.
 *
 * <p>
 * <strong>This is not a footnote, it is the point.</strong> M3a's band is knowingly too
 * tight: independence is a lie, and `roadmap.md` measured a shared team factor moving a
 * true P90 from 209.4 to 222.2 on ten tasks. A tool that reports a number without saying
 * so is doing the precise thing this product exists to replace, so every run carries its
 * own limitations and the screen prints them beside the answer rather than behind a link.
 *
 * <p>
 * <strong>Stored on the run, not derived when it is read</strong> — and M3b is where that
 * stopped being a precaution and started being load-bearing. The first two below describe
 * the engine that produced a run rather than the plan it forecast, and version 2 does
 * what they say it did not. A limitation worked out at read time would have made every
 * M3a run in the table silently claim a model it never had.
 *
 * <p>
 * <strong>Retired is not the same as deleted, and the difference is the history.</strong>
 * Nothing writes the first two any more; the constants stay because runs that carry them
 * are still read, and an enum missing a value that exists in stored JSON is a run that
 * cannot be deserialised at all. The plan for this milestone said "deleted", and deleting
 * them would have taken every forecast made before it with them.
 *
 * <p>
 * Codes rather than sentences, translated by the frontend like every other code this API
 * publishes.
 */
public enum ForecastLimitation {

	/**
	 * <strong>Retired by M3b: written by no run made under engine version 2.</strong>
	 * Nothing correlated. Every item was sampled independently, so good and bad luck
	 * cancelled out and the band was narrower than any real project's. Runs made before
	 * that go on saying so, which is the point of storing it.
	 */
	NO_TEAM_FACTOR("no_team_factor"),

	/**
	 * <strong>Retired by M3b: written by no run made under engine version 2.</strong>
	 * Only the work somebody had already written down was forecast. Projects overrun
	 * because of work nobody listed far more often than because a listed task exceeded
	 * its P90, and `product-concept.md` calls this usually the larger of the two
	 * uncertainties.
	 */
	NO_SCOPE_UNCERTAINTY("no_scope_uncertainty"),

	/**
	 * Some of the plan carries no estimate. Those items kept their place in the graph and
	 * contributed no effort, so the answer is short by however much they turn out to
	 * hold.
	 */
	UNESTIMATED_ITEMS("unestimated_items"),

	/**
	 * Somebody's middle number argues with their own two ends by more than a quarter. The
	 * fit uses the ends, so the estimate was used as given — this says only that the
	 * three numbers were not thought about together, which is the first thing M5 will
	 * want.
	 */
	INCONSISTENT_ESTIMATES("inconsistent_estimates"),

	/**
	 * An arrow pointed at work that has since been put away. Archived work is not going
	 * to happen, so waiting for it would be waiting forever, and the constraint was
	 * dropped — which is a thing to be told rather than to discover from a date that came
	 * out early.
	 */
	DEPENDENCIES_ON_ARCHIVED_WORK("dependencies_on_archived_work");

	private final String code;

	ForecastLimitation(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return this.code;
	}

}
