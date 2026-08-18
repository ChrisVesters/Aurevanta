package com.cvesters.aurevanta.calibration;

import com.cvesters.aurevanta.forecast.model.Proportion;

/**
 * How often a set of ranges contained the outcome, and how firmly that is known.
 *
 * <p>
 * <strong>One object rather than three fields, so that the rate cannot be published
 * without its interval.</strong> Four hits out of five is 80% and is consistent with a
 * team at 51% and one at 94%; a reader given the rate alone has been given the half of it
 * that means nothing. As three nullable numbers beside each other that was a convention
 * every caller had to keep — and one nobody could keep, since nothing in the shape said
 * the three are absent together. Here it is not a convention: there is no way to hold the
 * rate and not hold the bounds.
 *
 * @param value between 0 and 1. A well-judged set scores 0.8, and higher is not better.
 * @param low and {@code high} bound it at 80% — the same confidence as the band being
 * measured, so that a screen holds one convention rather than two.
 */
public record HitRateResponse(double value, double low, double high) {

	/**
	 * Null when nothing has been counted: nought out of nought is not a rate of nought.
	 */
	public static HitRateResponse of(Proportion proportion) {
		return proportion.measured() ? new HitRateResponse(proportion.rate(), proportion.low(), proportion.high())
				: null;
	}

}
