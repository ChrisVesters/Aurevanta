package com.cvesters.aurevanta.forecast.model;

/**
 * What a plan is likely to take, in hours of elapsed schedule.
 *
 * <p>
 * <strong>Hours, not dates.</strong> Turning effort into a calendar needs an assumption
 * about what a working day is worth, and the calendar is where that assumption gets made
 * visibly. Inventing one here would bury it in the one place `roadmap.md` says it must
 * not be buried.
 *
 * <p>
 * <strong>Five percentiles rather than two</strong>, and the odd one out is the P80: the
 * calendar's confidence control offers 50, 80 and 95, and a percentile nobody stored is a
 * re-run to answer.
 *
 * @param standardDeviationHours the spread, which is here because it is what the
 * closed-form oracle checks. A sum of independent draws has an exactly known variance
 * whatever shape they are, so this is the number that says whether the sampler is right
 * rather than merely plausible.
 */
public record Forecast(double meanHours, double standardDeviationHours, double p10Hours, double p50Hours,
		double p80Hours, double p90Hours, double p95Hours, Histogram histogram) {

}
