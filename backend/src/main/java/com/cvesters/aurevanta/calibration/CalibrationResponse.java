package com.cvesters.aurevanta.calibration;

import java.time.Instant;
import java.util.List;

/**
 * How often this organisation's ranges contained the truth.
 *
 * <p>
 * <strong>Three buckets, and nothing may add them together.</strong> They are the same
 * question asked of ranges written at three different moments relative to the work:
 * beforehand, afterwards, and where nobody ever said when the work began. Only the first
 * is a forecast. The second is expected to be very good and says how large hindsight is
 * on this team's own work; the third cannot be told from the second and is named rather
 * than guessed at.
 *
 * <p>
 * <strong>The two breakdowns split the forecasts and only the forecasts.</strong>
 *
 * @param firstScored and {@code lastScored} are when the earliest and latest scored
 * estimates were <em>written</em> — moments the server observed, not days anybody
 * reported, so a client converts them to where its reader is sitting. They are how a
 * reader tells a current record from one describing how the team estimated a year ago.
 * Null together when nothing has been scored.
 */
public record CalibrationResponse(CalibrationRecordResponse forecasts, CalibrationRecordResponse reports,
		CalibrationRecordResponse unbounded, List<EstimatorCalibrationResponse> byEstimator,
		List<MethodCalibrationResponse> byMethod, CalibrationCoverageResponse coverage, Instant firstScored,
		Instant lastScored) {

	public static CalibrationResponse of(OrganisationCalibration calibration) {
		return new CalibrationResponse(CalibrationRecordResponse.of(calibration.forecasts()),
				CalibrationRecordResponse.of(calibration.reports()),
				CalibrationRecordResponse.of(calibration.unbounded()),
				calibration.byEstimator().stream().map(EstimatorCalibrationResponse::of).toList(),
				calibration.byMethod().stream().map(MethodCalibrationResponse::of).toList(),
				CalibrationCoverageResponse.of(calibration.coverage()), calibration.firstScored(),
				calibration.lastScored());
	}

}
