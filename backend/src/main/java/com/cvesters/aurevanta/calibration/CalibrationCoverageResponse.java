package com.cvesters.aurevanta.calibration;

/**
 * What could not be scored, and why.
 *
 * <p>
 * <strong>The most-read part of this response for most of a year.</strong> Scoring
 * anything needs finished work carrying both an estimate and a measured actual, and the
 * actual is optional in every state because most teams do not track it — so the ordinary
 * answer is that nothing has been scored yet, and a screen that says only that guarantees
 * it stays true. Each figure is a different thing to go and do.
 */
public record CalibrationCoverageResponse(long completedItems, long withActual, long withEstimate, int scoredItems,
		int movedByTheStartDay) {

	public static CalibrationCoverageResponse of(CalibrationCoverage coverage) {
		return new CalibrationCoverageResponse(coverage.completedItems(), coverage.withActual(),
				coverage.withEstimate(), coverage.scoredItems(), coverage.movedByTheStartDay());
	}

}
