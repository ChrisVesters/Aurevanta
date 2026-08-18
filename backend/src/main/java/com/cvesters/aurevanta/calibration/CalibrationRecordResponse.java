package com.cvesters.aurevanta.calibration;

import com.cvesters.aurevanta.forecast.model.Calibration;

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
 * @param rate and {@code corrections} are each null until there is evidence for them, and
 * each is one object rather than a handful of loose fields — {@link HitRateResponse} and
 * {@link CorrectionsResponse} say why that is the point rather than tidiness.
 * @param belowP10 and {@code aboveP90} are which way the misses went. All above is
 * optimism; both ways is a band too tight, and they are different things to do something
 * about.
 * @param pointEstimates how many of these claimed certainty — three identical numbers.
 * They count in the rate and cannot count in the two corrections, so the denominators
 * differ and this is what says by how much.
 */
public record CalibrationRecordResponse(int scored, int hits, int belowP10, int aboveP90, int pointEstimates,
		HitRateResponse rate, CorrectionsResponse corrections) {

	public static CalibrationRecordResponse of(Calibration record) {
		return new CalibrationRecordResponse(record.estimates(), record.hits(), record.belowP10(), record.aboveP90(),
				record.pointEstimates(), HitRateResponse.of(record.hitRate()), CorrectionsResponse.of(record));
	}

}
