package com.cvesters.aurevanta.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.cvesters.aurevanta.forecast.model.Drift;
import com.cvesters.aurevanta.forecast.model.ForecastTerms;

/**
 * Whether this plan's date keeps moving out, read off the history beside it.
 *
 * <p>
 * <strong>On the listing rather than on an endpoint of its own</strong>, because it is a
 * property of the whole history and not of any one run in it — and because a screen
 * already showing that history should not have to ask twice for the one sentence somebody
 * opened it for. It costs no simulation: every date it reads is stored, and the rest is
 * subtraction.
 *
 * @param sinceRunId the oldest run this drift is measured from, which is the oldest one
 * that answered the same question. A reader can find it in the same payload and check the
 * arithmetic.
 * @param runs how many forecasts the window holds. One means the window is the newest run
 * alone — either it is the plan's first, or the one before it was asked something
 * different — and the drift is nought rather than absent, because a plan has not drifted
 * from itself.
 */
public record DriftResponse(UUID sinceRunId, int runs, List<DriftAtResponse> at) {

	/**
	 * The verdict over a plan's runs as the listing returns them.
	 * @param newestFirst every forecast of one plan, newest first
	 * @return null when there are none at all — a plan nobody has forecast has no date to
	 * have moved, which is a different answer from a plan whose date has held still
	 */
	static DriftResponse over(List<ForecastRun> newestFirst) {
		if (newestFirst.isEmpty()) {
			return null;
		}
		List<ForecastTerms> terms = newestFirst.stream().map(ForecastService::termsOf).toList();
		List<DriftAtResponse> at = new ArrayList<>(ForecastService.CONFIDENCES.length);
		// The window is decided by what each run was asked, which no confidence changes,
		// so all three agree about how far back it reaches and any one of them may say
		// so.
		int runs = 0;
		for (int confidence : ForecastService.CONFIDENCES) {
			Drift drift = Drift.since(readings(newestFirst, terms, confidence));
			runs = drift.runs();
			at.add(new DriftAtResponse(confidence, drift.fromDate(), drift.toDate(), drift.days(), drift.bandDays(),
					drift.movingOut()));
		}
		return new DriftResponse(newestFirst.get(runs - 1).getId(), runs, at);
	}

	/**
	 * Each run as the detector reads it: its terms, its date at this confidence, and its
	 * own band's two ends.
	 *
	 * <p>
	 * The days come through {@link ForecastResponse#dateOf} and not from a calendar
	 * applied here, so a run made under a rule this version cannot read reports no drift
	 * for the same reason it reports no dates — rather than being quietly resolved under
	 * today's.
	 */
	private static List<Drift.Reading> readings(List<ForecastRun> newestFirst, List<ForecastTerms> terms,
			int confidence) {
		List<Drift.Reading> readings = new ArrayList<>(newestFirst.size());
		for (int index = 0; index < newestFirst.size(); index++) {
			ForecastRun run = newestFirst.get(index);
			readings.add(new Drift.Reading(terms.get(index), ForecastResponse.dateOf(run, run.hoursAt(confidence)),
					ForecastResponse.dateOf(run, run.getP10Hours()), ForecastResponse.dateOf(run, run.getP90Hours())));
		}
		return readings;
	}

}
