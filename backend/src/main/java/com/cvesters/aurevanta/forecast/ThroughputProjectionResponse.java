package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;

import com.cvesters.aurevanta.forecast.model.ThroughputForecast;

/**
 * When the backlog runs out, in weeks and in days.
 *
 * <p>
 * <strong>Both, for M4's reason.</strong> The weeks are what the history produced and the
 * dates are one presentation of them; publishing only the date would hide the unit the
 * answer was computed in, and a reader comparing this with the engine's band needs to see
 * that one is counted in wall-clock weeks and the other derived from effort through a
 * working day.
 *
 * <p>
 * <strong>No working day appears anywhere in here.</strong> A week of history is a week
 * of wall clock with its holidays and its Friday afternoons already inside it, so the
 * date is the as-of day plus that many weeks. Dividing by a working day would be M4's own
 * error — capacity counted twice — arriving from the other side.
 *
 * @param seed a string and not a number, because it is sixty-four bits and a JSON number
 * is a double in a browser: as a number nearly every seed published would arrive silently
 * rounded, and a seed that is nearly right reproduces nothing.
 * @param sampleCount fixed rather than asked for. This endpoint takes a date and nothing
 * else, and that asymmetry with the engine's five assumptions is most of why the
 * comparison is worth having.
 */
public record ThroughputProjectionResponse(double meanWeeks, int p10Weeks, int p50Weeks, int p80Weeks, int p90Weeks,
		int p95Weeks, LocalDate p10Date, LocalDate p50Date, LocalDate p80Date, LocalDate p90Date, LocalDate p95Date,
		String seed, int sampleCount) {

	public static ThroughputProjectionResponse of(ThroughputForecast forecast, LocalDate asOf, long seed,
			int sampleCount) {
		return new ThroughputProjectionResponse(forecast.meanWeeks(), forecast.p10Weeks(), forecast.p50Weeks(),
				forecast.p80Weeks(), forecast.p90Weeks(), forecast.p95Weeks(), asOf.plusWeeks(forecast.p10Weeks()),
				asOf.plusWeeks(forecast.p50Weeks()), asOf.plusWeeks(forecast.p80Weeks()),
				asOf.plusWeeks(forecast.p90Weeks()), asOf.plusWeeks(forecast.p95Weeks()), Long.toString(seed),
				sampleCount);
	}

}
