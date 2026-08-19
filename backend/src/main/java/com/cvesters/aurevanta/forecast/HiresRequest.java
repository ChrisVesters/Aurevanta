package com.cvesters.aurevanta.forecast;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A pool somebody is thinking of adding to, and how many.
 *
 * <p>
 * <strong>One pool at a time, and no search.</strong> The inverse query searches because
 * a list of cuts is combinatorial and its budget has to be stated; this is a question
 * with a handful of answers, and a search over hiring plans would be a staffing tool
 * rather than a forecast. Which pool to ask about is a judgement about people and money
 * that this server holds none of — the same reason the candidates for a cut come from the
 * caller.
 *
 * @param units how many to add, answered one at a time up to that many so that the
 * <em>diminishing return</em> is visible rather than inferred. It is the whole answer to
 * "should we hire": the second person is worth less than the first, and how much less is
 * the thing nobody can feel their way to.
 */
public record HiresRequest(@NotNull UUID resourceId,

		@NotNull @Positive @Max(MOST_UNITS) Integer units) {

	/**
	 * How many may be asked about at once — each is a whole simulation, and a team that
	 * doubles is a different plan rather than a bigger one.
	 */
	static final int MOST_UNITS = 10;

}
