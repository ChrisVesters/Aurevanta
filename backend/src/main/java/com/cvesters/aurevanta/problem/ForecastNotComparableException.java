package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Two forecasts that cannot be set beside each other.
 *
 * <p>
 * Either they are of different plans, or they were made by different versions of the
 * engine. The second is M6's argument rather than a fussy check: a comparison across a
 * version bump is not a rougher comparison, it is an exact account of a movement that
 * never happened — and it would look entirely reasonable.
 *
 * <p>
 * <strong>Everything else two runs can disagree about is reported rather than
 * refused.</strong> Somebody adjusting the capacity is exactly what a decomposition
 * exists to tell them; refusing that pair would leave them staring at two dates a
 * fortnight apart with no account of either.
 */
public class ForecastNotComparableException extends ApiProblemException {

	public ForecastNotComparableException() {
		super(HttpStatus.BAD_REQUEST, "Forecasts not comparable", "forecast_not_comparable",
				"These two forecasts cannot be compared");
	}

}
