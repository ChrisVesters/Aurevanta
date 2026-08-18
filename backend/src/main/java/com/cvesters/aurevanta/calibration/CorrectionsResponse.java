package com.cvesters.aurevanta.calibration;

import com.cvesters.aurevanta.forecast.model.Calibration;

/**
 * What a set of ranges got wrong, in the two ways a range can be wrong.
 *
 * <p>
 * <strong>One object rather than two fields, for the reason the hit rate is one.</strong>
 * Bias and spread are different failures and neither statistic sees the other: an
 * estimator who is reliably low with well-judged widths and one whose widths are three
 * times what they need are both badly calibrated and need opposite corrections.
 * Publishing either alone would let a screen render the half that reads as a target —
 * which is how a hit rate comes to be gamed by writing one to a thousand hours.
 *
 * @param medianPercentile where the truth typically landed on the estimator's own scale.
 * 0.5 is unbiased. A screen renders this as a <em>position</em> rather than a number,
 * because nobody can reason about a percentile.
 * @param bandWidthMultiplier how many times wider the ranges should have been. 1 is
 * right, and below 1 is the number that catches a range padded until it contains
 * everything.
 */
public record CorrectionsResponse(double medianPercentile, double bandWidthMultiplier) {

	/**
	 * Null below two outcomes: a spread needs two, and the bias is withheld with it
	 * rather than published alone.
	 */
	public static CorrectionsResponse of(Calibration record) {
		return record.corrected() ? new CorrectionsResponse(record.medianPercentile(), record.bandWidthMultiplier())
				: null;
	}

}
