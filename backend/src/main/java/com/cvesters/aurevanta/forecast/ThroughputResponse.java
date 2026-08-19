package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What a plan's own history says about when it will be finished.
 *
 * <p>
 * <strong>A second opinion, and never a tiebreaker.</strong> Nothing here averages with
 * the engine's band, corrects it, or resolves a disagreement between them: two forecasts
 * that disagree are the output. "The team says six weeks and their own history says
 * eleven" is an argument that starts a conversation, and one number in the middle ends it
 * with nobody having learnt anything.
 *
 * <p>
 * <strong>The window and the projection are separate objects because they are separately
 * absent.</strong> A plan nobody has finished anything in has neither; a plan with a
 * month of history has a window and no projection worth publishing. Grouping each fact
 * into its own nullable object is calibration's rule — there is no shape here that holds
 * half of either.
 *
 * @param remaining how much is left to deliver, unestimated items included: what is
 * counted is work left rather than effort left, which is the one place this is better
 * informed than the engine.
 * @param rule which week this history was cut into, published for the reason a run
 * publishes its calendar — two defensible definitions give two different histories from
 * identical data.
 * @param burnUp what has been delivered week by week and what the same history says about
 * the weeks ahead. It is the window and the projection drawn rather than summarised, and
 * it is the same numbers: nothing here is a third forecast.
 * @param limitations what this answer did not do, beside the answer rather than behind a
 * link.
 */
public record ThroughputResponse(UUID projectId, LocalDate asOf, String rule, int remaining,
		ThroughputWindowResponse window, ThroughputProjectionResponse projection, BurnUpResponse burnUp,
		List<ThroughputLimitation> limitations) {

}
