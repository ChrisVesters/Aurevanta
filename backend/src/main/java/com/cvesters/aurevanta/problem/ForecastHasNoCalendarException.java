package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A question about a date was asked of a run that has no calendar to read one with.
 *
 * <p>
 * Every run made before M4 is one of these: it produced hours and no date, because nobody
 * had stated a working day yet, and nothing backfilled one onto it. So "can this hit 1
 * November?" cannot be turned into a number of hours for it, and the honest answer is
 * that the question does not apply rather than an answer under a working day this server
 * picked.
 *
 * <p>
 * The same rule the dates themselves follow, in the only form that fits an inverse query:
 * a forecast without a calendar reports no dates, and cannot be asked about one either.
 *
 * <p>
 * Unprocessable rather than a bad request, because nothing about the request is wrong. It
 * is a well-formed question about a run that cannot answer it — the same shape
 * {@code nothing_to_forecast} takes.
 */
public class ForecastHasNoCalendarException extends ApiProblemException {

	public ForecastHasNoCalendarException() {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "Forecast has no calendar", "forecast_has_no_calendar",
				"This run was made before anybody stated a working day, so it cannot be asked about a date");
	}

}
