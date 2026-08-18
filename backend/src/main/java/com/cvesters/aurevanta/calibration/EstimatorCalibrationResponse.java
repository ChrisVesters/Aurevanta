package com.cvesters.aurevanta.calibration;

import java.util.UUID;

/**
 * One person's own record, over the forecasts they wrote before the work began.
 *
 * <p>
 * Carries the name as well as the identifier, the way an estimate does: a screen showing
 * this would otherwise have to hold the member list to say who it is about, and somebody
 * who has left is not on that list while their estimates are still here.
 */
public record EstimatorCalibrationResponse(UUID estimatorId, String estimatorName, CalibrationRecordResponse record) {

	public static EstimatorCalibrationResponse of(EstimatorCalibration estimator) {
		return new EstimatorCalibrationResponse(estimator.estimatorId(), estimator.estimatorName(),
				CalibrationRecordResponse.of(estimator.record()));
	}

}
