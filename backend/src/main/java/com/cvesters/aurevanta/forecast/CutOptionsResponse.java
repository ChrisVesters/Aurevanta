package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.util.List;

/**
 * What it would take to hit a date, measured against one stored run.
 *
 * <p>
 * <strong>The hours are on the answer because the date is not the whole of the
 * question.</strong> A target date only means something under a working day and a
 * calendar, and this run carries both — so the budget it became is shown rather than left
 * implicit. That is M4's rule about a stated assumption arriving beside the number it
 * produced, in the one place where the number is a recommendation.
 *
 * @param targetHours what the date came to, through the run's own calendar
 * @param baselineConfidence the share of this run that already beat the date, as a
 * percentage — the number every cut below is measured against
 * @param meets whether the plan already clears the bar, in which case the list below is
 * advice nobody needs
 * @param simulations how many times the plan was run to answer this, which is what the
 * budget in {@code CutsRequest} bounds. Reported rather than hidden: a search that
 * stopped early and did not say so is a search reporting the best thing it happened to
 * look at.
 * @param cuts each candidate and what it is worth <strong>on its own</strong>, largest
 * first. They do not add up — see {@link CutResponse}.
 */
public record CutOptionsResponse(BigDecimal targetHours, double baselineConfidence, boolean meets, int simulations,
		List<CutResponse> cuts) {

}
