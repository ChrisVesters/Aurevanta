package com.cvesters.aurevanta.forecast.model;

import java.util.List;

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
 * @param trajectory how much had been delivered by the end of each week, week by week,
 * from which a burn-up's cone is drawn. It is the thing the loop walks over rather than a
 * second answer: the five figures above are where it <em>stopped</em>, and this is the
 * route it took to get there, so the picture and the number cannot disagree about a plan.
 */
public record ThroughputForecast(double meanWeeks, double standardDeviationWeeks, int p10Weeks, int p50Weeks,
		int p80Weeks, int p90Weeks, int p95Weeks, int unfinishedRuns, List<Delivered> trajectory) {

	public ThroughputForecast {
		trajectory = List.copyOf(trajectory);
	}

	/**
	 * How much had been delivered by the end of one week, as a band.
	 *
	 * <p>
	 * <strong>These percentiles run the other way from the ones above and it is the
	 * easiest thing here to misread.</strong> {@code p90Weeks} is the
	 * <em>pessimistic</em> end of a finish date — nine runs in ten were done by then —
	 * while {@link #p90} is the <em>optimistic</em> end of a delivery count, because
	 * delivering more is the good outcome. So the low edge of a cone is {@link #p10}, and
	 * it is the one a reader should be planning against.
	 *
	 * <p>
	 * <strong>Nothing here exceeds the backlog, and that is why a cone narrows.</strong>
	 * A run that covers the backlog stops, so every run converges on the same number and
	 * the band closes — which is a ceiling rather than uncertainty falling away, and is
	 * worth knowing before reading confidence into the shape of the picture.
	 *
	 * @param week how many weeks after the day being asked about, so week zero is the
	 * question itself and delivers nothing.
	 */
	public record Delivered(int week, int p10, int p50, int p90) {
	}

}
