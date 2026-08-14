package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * No forecast with that identifier belongs to the caller's organisation.
 *
 * <p>
 * One answer for "there is no such run" and "it belongs to another organisation", as
 * {@link ProjectNotFoundException} is: telling them apart would make the endpoint a way
 * to discover which identifiers exist elsewhere.
 */
public class ForecastNotFoundException extends ApiProblemException {

	public ForecastNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Forecast not found", "forecast_not_found",
				"No forecast with that identifier belongs to this organisation");
	}

}
