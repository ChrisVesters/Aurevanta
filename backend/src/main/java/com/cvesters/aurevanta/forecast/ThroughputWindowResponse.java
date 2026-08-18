package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;

import com.cvesters.aurevanta.forecast.model.Throughput;

/**
 * The weeks a projection was drawn from, which is as much of the answer as the answer is.
 *
 * <p>
 * <strong>{@link #worst()} is the most useful number here.</strong> A bootstrap cannot
 * draw a week worse than the worst one in its window, so a reader who knows their team
 * stops for a week each quarter can tell at a glance whether the window contains such a
 * week — and if it does not, the forecast beside it is early and confident for a reason
 * no arithmetic reports. That is why the window ships even when the projection does not.
 *
 * @param weeks how many, empty ones included: a week nobody finished anything in is a
 * week the team had, and leaving it out would inflate the rate by exactly the fraction of
 * the time nothing was delivered.
 * @param perWeek the average, which is the number a reader will quote and the one the
 * projection does <em>not</em> use — resampling exists because a mean cannot tell
 * ten-and- nothing from a steady five.
 */
public record ThroughputWindowResponse(int weeks, LocalDate from, LocalDate to, int completed, double perWeek, int best,
		int worst) {

	/**
	 * Null when nothing has ever been finished: no weeks, and so no window to describe.
	 */
	public static ThroughputWindowResponse of(Throughput history) {
		return history.observed() ? new ThroughputWindowResponse(history.weekCount(), history.from(), history.to(),
				history.completed(), history.perWeek(), history.best(), history.worst()) : null;
	}

}
