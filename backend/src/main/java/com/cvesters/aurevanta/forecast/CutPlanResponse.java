package com.cvesters.aurevanta.forecast;

import java.util.List;

/**
 * A list of things to drop that gets to the date, in the order to drop them.
 *
 * <p>
 * <strong>A list, not the list.</strong> It is found greedily — take the best, then the
 * best of what is left <em>with that one already gone</em>, and stop — because the
 * optimal subset needs two-to-the-n evaluations and each of those is a whole simulation.
 * Greedy can miss a pair that only works together: two halves of one feature, neither of
 * which shortens the path alone. Somebody who wants a different combination names a
 * different candidate set, which is the same answer decision 1 gives to everything else
 * about which work is negotiable.
 *
 * <p>
 * <strong>Every step was measured.</strong> Nothing here is the sum of what the singles
 * were worth — that is precisely the arithmetic that does not hold.
 *
 * @param steps what to drop, in order. Empty when the plan already clears the bar, which
 * is an answer rather than an absence of one.
 * @param ending why the search stopped, which is as much of the answer as the list is.
 */
public record CutPlanResponse(List<CutStepResponse> steps, CutSearchEnding ending) {

}
