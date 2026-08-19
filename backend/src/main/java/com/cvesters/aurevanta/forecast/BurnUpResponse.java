package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.cvesters.aurevanta.forecast.model.Throughput;
import com.cvesters.aurevanta.forecast.model.ThroughputForecast;

/**
 * What has been delivered and what is left, week by week, with the future as a cone.
 *
 * <p>
 * <strong>One series in two halves, and both are in the same units on the same
 * scale.</strong> The past is a running total of finished work and the cone continues
 * from where it stopped, so a reader is never asked to add the two together — which is
 * the arithmetic that gets done wrong, and is why the cone carries totals rather than the
 * increments the model produced.
 *
 * <p>
 * <strong>The cone comes from the throughput forecast's bootstrap and never from the
 * engine.</strong> A burn-up's future is how many <em>items</em> are done by each week,
 * and the engine has no notion of that: it forecasts effort and reports when a plan
 * finishes, so inventing a trajectory from its finish distribution would mean assuming a
 * shape nothing measured. The bootstrap already walks a week at a time, from the same
 * history, the same sampler and the same seed as the date published beside it — so the
 * picture and the number cannot disagree.
 *
 * @param delivered what is finished today and {@code total} what there is to finish,
 * which are the two numbers the sentence over the picture says out loud. Both are
 * derivable from figures elsewhere in this answer, and a reader deriving them is a reader
 * who might not. <strong>This is the first place the two counts are added together, and
 * they count archived work by opposite rules on purpose</strong> —
 * {@code WorkItemRepository.completionsInProject} keeps work that was delivered and later
 * put away, because dropping it would make tidying up look like a slowdown, and
 * {@code countRemainingInProject} drops work put away before it was finished, because
 * that is not going to be delivered at all. So a team that archives finished work reads
 * "delivered 300 of 340" over a plan screen showing forty rows, and both halves are
 * right: the total is what this plan will have delivered when it is done.
 * @param past every week from the first completion to the day being asked about, empty
 * ones included — a flat stretch is a fortnight nothing was delivered in, and it is as
 * much of the shape as the climbs are.
 * @param cone null exactly when the projection is, and for the same three reasons:
 * nothing left to deliver, too little history to resample, or a rate that does not clear
 * the backlog inside the horizon. A plan in any of those states still draws its past.
 */
public record BurnUpResponse(int delivered, int total, List<BurnUpWeekResponse> past,
		List<BurnUpConeWeekResponse> cone) {

	/**
	 * The picture, or nothing at all when this plan has never finished anything.
	 *
	 * <p>
	 * A plan with no history has no past to draw and no cone to draw either, which is the
	 * state {@link ThroughputWindowResponse} is already null in. Drawing an empty axis
	 * would be a chart of nothing, and the limitation beside it already says why.
	 */
	static BurnUpResponse of(Throughput history, int remaining, ThroughputForecast forecast, LocalDate asOf) {
		if (!history.observed()) {
			return null;
		}
		int delivered = history.completed();
		return new BurnUpResponse(delivered, delivered + remaining, past(history), cone(forecast, delivered, asOf));
	}

	private static List<BurnUpWeekResponse> past(Throughput history) {
		int[] delivered = history.deliveredByWeek();
		List<BurnUpWeekResponse> weeks = new ArrayList<>(delivered.length);
		for (int week = 0; week < delivered.length; week++) {
			weeks.add(new BurnUpWeekResponse(history.from().plusWeeks(week), delivered[week]));
		}
		return weeks;
	}

	/**
	 * The cone, carried up from what is already finished so that it continues the line
	 * above rather than starting again from zero.
	 */
	private static List<BurnUpConeWeekResponse> cone(ThroughputForecast forecast, int delivered, LocalDate asOf) {
		if (forecast == null) {
			return null;
		}
		List<BurnUpConeWeekResponse> weeks = new ArrayList<>(forecast.trajectory().size());
		for (ThroughputForecast.Delivered week : forecast.trajectory()) {
			weeks.add(new BurnUpConeWeekResponse(asOf.plusWeeks(week.week()), delivered + week.p10(),
					delivered + week.p50(), delivered + week.p90()));
		}
		return weeks;
	}

}
