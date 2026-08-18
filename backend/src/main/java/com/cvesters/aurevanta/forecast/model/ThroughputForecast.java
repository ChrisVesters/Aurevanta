package com.cvesters.aurevanta.forecast.model;

/**
 * When a backlog runs out, in whole weeks from the day it was asked about.
 *
 * <p>
 * <strong>Weeks and not hours, and that is not a unit conversion away from
 * {@link Forecast}.</strong> The engine answers in effort and needs a working day before
 * it can name a date; this counts wall-clock weeks in which a team delivered whatever it
 * delivered, so the holidays and the incident and the Friday afternoons are already
 * inside the number. Multiplying it by a working day would be M4's own error — capacity
 * counted twice — arriving from the other side.
 *
 * <p>
 * <strong>Whole weeks, because the model has no finer grain.</strong> A bootstrap draws a
 * week at a time, so an answer of "eight" means the backlog was covered somewhere inside
 * the eighth week and not that it lands on its Sunday. Interpolating between two order
 * statistics would be precision invented on top of a number that does not have it, which
 * is {@code Engine}'s reason for taking the nearest rank and is the same reason here.
 *
 * <p>
 * The five percentiles are the engine's five, so that M4's confidence control means the
 * same thing on both answers and a reader comparing them is comparing like with like.
 *
 * @param standardDeviationWeeks the spread, which is here because it is what the oracle
 * checks: a team that finished exactly the same number every week must answer with zero.
 * @param unfinishedRuns how many runs did not cover the backlog inside
 * {@link Throughput#MOST_WEEKS}. Zero for anything worth forecasting, and published
 * rather than swallowed — a percentile standing at the horizon is a censored number, and
 * a reader has to be able to tell that from a plan that really does take ten years.
 */
public record ThroughputForecast(double meanWeeks, double standardDeviationWeeks, int p10Weeks, int p50Weeks,
		int p80Weeks, int p90Weeks, int p95Weeks, int unfinishedRuns) {

}
