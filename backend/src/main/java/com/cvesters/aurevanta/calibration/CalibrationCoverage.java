package com.cvesters.aurevanta.calibration;

/**
 * What could not be scored, and why — which for most organisations is the whole of what
 * this work has to say for its first year.
 *
 * <p>
 * <strong>Designed as the main answer rather than as a fallback.</strong> A calibration
 * record needs finished work that carries both an estimate and a measured actual, and
 * {@code actual_effort_hours} is optional in every state because most teams do not track
 * it. So the ordinary reading of this endpoint is "nothing yet", and a screen that says
 * only that guarantees there will continue to be nothing. Each number here is a different
 * thing to go and do.
 *
 * @param completedItems every item reported as done, archived ones included.
 * @param withActual how many of those said how long they took. The gap to
 * {@code completedItems} is work that can never be scored unless somebody goes back and
 * fills it in.
 * @param withEstimate how many of them anybody had estimated. The gap here is work that
 * was never a prediction, so there is nothing to have been right or wrong about.
 * @param scoredItems how many had both, and so appear somewhere in the three buckets.
 * @param movedByTheStartDay how many estimates were counted as reports rather than
 * forecasts <em>only</em> because they were written on the day the work began. Published
 * because decision 1 pays a real price for refusing to guess at a time of day: if this
 * turns out to hold most of an organisation's estimates, that has to arrive as a number
 * somebody can see rather than as a quietly better hit rate.
 */
public record CalibrationCoverage(long completedItems, long withActual, long withEstimate, int scoredItems,
		int movedByTheStartDay) {
}
