package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A run scheduled against a capacity was asked what hiring would be worth.
 *
 * <p>
 * <strong>Refused rather than answered, and the line is worth being precise
 * about.</strong> A capacity is one undifferentiated pool, so "one more" is a question
 * with an answer — and it is the question the forecast form already asks, one field away,
 * by running the plan again with a larger number. What this endpoint exists for is the
 * question that only makes sense once a team has been described: <em>which</em> pool is
 * worth adding to. Answering it for a plan with no pools would be answering the easy
 * question in the hard question's clothes.
 */
public class ForecastHasNoResourcesException extends ApiProblemException {

	public ForecastHasNoResourcesException() {
		super(HttpStatus.BAD_REQUEST, "Forecast has no resources", "forecast_has_no_resources",
				"This forecast was made against a capacity rather than a team, so there is no resource to add to");
	}

}
