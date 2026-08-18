package com.cvesters.aurevanta.calibration;

import com.cvesters.aurevanta.forecast.model.Calibration;
import com.cvesters.aurevanta.forecast.model.Proportion;

/**
 * One set of ranges and what they turned out to be worth, as the API describes it.
 *
 * <p>
 * <strong>Counts are always numbers and rates are null until there is evidence for
 * them.</strong> Nought out of nought is not a rate of nought, and "0% of your estimates
 * landed inside their range" is a sentence this API must never send to an organisation
 * that has not finished anything. Zero completed items is a true zero; zero per cent is a
 * claim.
 *
 * <p>
 * <strong>The rate and its interval travel together and neither ships without the
 * multiplier.</strong> Four hits out of five is 80% and means nothing — the interval is
 * what says so. And a hit rate on its own is gamed by widening every range until it
 * always contains the outcome, which {@code bandWidthMultiplier} reports as a number
 * below one. A client is free to render badly; what this response will not do is make
 * rendering badly the path of least resistance by publishing the rate alone.
 *
 * @param hitRate between 0 and 1, and 0.8 is what a well-calibrated set scores — a
 * P10–P90 band is meant to be wrong one time in five.
 * @param hitRateLow and {@code hitRateHigh} bound it at 80%, the same confidence as the
 * band being measured, so that a screen holds one convention rather than two.
 * @param belowP10 and {@code aboveP90} are which way the misses went. All above is
 * optimism; both ways is a band too tight, and they are different things to do something
 * about.
 * @param medianPercentile where the truth typically landed on the estimator's own scale.
 * 0.5 is unbiased.
 * @param bandWidthMultiplier how many times wider the ranges should have been. 1 is
 * right.
 * @param pointEstimates how many of these claimed certainty — three identical numbers.
 * They count in the rate and cannot count in the two corrections, so the denominators
 * differ and this is what says by how much.
 */
public record CalibrationRecordResponse(int scored, int hits, Double hitRate, Double hitRateLow, Double hitRateHigh,
		int belowP10, int aboveP90, Double medianPercentile, Double bandWidthMultiplier, int pointEstimates) {

	public static CalibrationRecordResponse of(Calibration record) {
		Proportion rate = record.hitRate();
		boolean measured = rate.measured();
		boolean corrected = record.corrected();
		return new CalibrationRecordResponse(record.estimates(), record.hits(), measured ? rate.rate() : null,
				measured ? rate.low() : null, measured ? rate.high() : null, record.belowP10(), record.aboveP90(),
				corrected ? record.medianPercentile() : null, corrected ? record.bandWidthMultiplier() : null,
				record.pointEstimates());
	}

}
