package com.cvesters.aurevanta.calibration;

/**
 * How the ranges collected one way turned out, against the ranges collected another.
 *
 * <p>
 * <strong>The answer to M5's own question, and the only one there will ever be.</strong>
 * That milestone changed what estimators are asked and could not prove the change was
 * worth anything; this is the split that settles it. The method is the name as stored, so
 * a value this server has never heard of arrives as an unrecognised name rather than as
 * nothing.
 */
public record MethodCalibrationResponse(String method, CalibrationRecordResponse record) {

	public static MethodCalibrationResponse of(MethodCalibration method) {
		return new MethodCalibrationResponse(method.method(), CalibrationRecordResponse.of(method.record()));
	}

}
