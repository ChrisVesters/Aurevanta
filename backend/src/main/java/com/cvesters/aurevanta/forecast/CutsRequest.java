package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A date somebody wants, and the work they are willing to drop to get it.
 *
 * <p>
 * <strong>The candidates come from the caller, and that is the milestone's first
 * decision.</strong> Which work is negotiable is a judgement about its value, and nothing
 * in this schema records any: a task worth four weeks that a regulator requires is not a
 * candidate, and a two-day nicety is. A server that proposed its own list would be
 * recommending that somebody delete work because it happened to sit on the deciding path.
 * The person says what is droppable; this says what each is worth.
 *
 * <p>
 * It also bounds the cost honestly. Every candidate is a whole simulation, so a request
 * that weighed the plan would be four minutes at the scale this product supports — and a
 * shortlist chosen by a heuristic would be wrong in a direction: ranking by contribution
 * to the <em>spread</em> hides the work that always takes exactly as long as it says,
 * which is frequently the best thing to cut.
 *
 * @param by the day somebody wants it done by, read through the run's own calendar — so a
 * forecast made before there was one cannot be asked at all.
 * @param confidence the bar, as a percentage. A hundred is allowed and means every run of
 * this forecast came in: a statement about the sample rather than a promise about the
 * world.
 * @param candidates the work that could be dropped, by identifier. May be empty, which
 * asks the one question that needs no candidates — can this be done as things stand?
 */
public record CutsRequest(@NotNull LocalDate by,

		@NotNull @Positive @Max(100) Integer confidence,

		@NotNull List<UUID> candidates) {

	/**
	 * As many things as may be weighed at once, because each one costs a simulation.
	 *
	 * <p>
	 * Refused rather than truncated when exceeded: a list silently cut to the first
	 * twelve would answer confidently about a set nobody chose, and the thirteenth might
	 * have been the one worth dropping.
	 */
	static final int MOST_CANDIDATES = 12;

	/**
	 * As many runs of the plan as one request may spend looking for a set that reaches
	 * the bar.
	 *
	 * <p>
	 * <strong>The search grows with the square of what is offered</strong>: choosing each
	 * next cut means weighing every candidate still in play, with everything already
	 * chosen already gone. Twelve candidates that never reach the bar would be
	 * seventy-eight runs, and at the five hundred items a plan may hold each of those is
	 * half a second.
	 *
	 * <p>
	 * Three times the cost of weighing the candidates once, so the search may look about
	 * three moves deep at full width — and when it stops for this reason it says so,
	 * because "as far as it looked" and "as far as it goes" are different answers.
	 */
	static final int MOST_SIMULATIONS = 40;

}
