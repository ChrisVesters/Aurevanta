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
 * <strong>Stored on the run, not derived when it is read.</strong> The first two are
 * properties of the engine version that produced it, so once M3b builds what they name, a
 * run made today must go on saying it lacked them. A limitation worked out at read time
 * would quietly disappear from history the moment the gap was closed.
 *
 * <p>
 * Codes rather than sentences, translated by the frontend like every other code this API
 * publishes.
 */
public enum ForecastLimitation {

	/**
	 * Nothing correlates. Every item was sampled independently, so good and bad luck
	 * cancel out and the band is narrower than any real project's. M3b deletes this one.
	 */
	NO_TEAM_FACTOR("no_team_factor"),

	/**
	 * Only the work somebody had already written down was forecast. Projects overrun
	 * because of work nobody listed far more often than because a listed task exceeded
	 * its P90, and `product-concept.md` calls this usually the larger of the two
	 * uncertainties. M3b deletes this one too.
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
