package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A run was asked what hiring into a pool it never had would be worth.
 *
 * <p>
 * <strong>The same refusal {@link CandidateNotInForecastException} makes about
 * work</strong>, for the same reason: a counterfactual is only meaningful against the
 * plan that was actually forecast, and adding to a pool the run never scheduled against
 * would answer a question about a team nobody had. A pool declared since the run is
 * exactly that.
 *
 * <p>
 * The remedy is the same too — ask for a new forecast, and it will be scheduled against
 * the team as it stands now.
 */
public class ResourceNotInForecastException extends ApiProblemException {

	public ResourceNotInForecastException() {
		super(HttpStatus.BAD_REQUEST, "Resource not in forecast", "resource_not_in_forecast",
				"That resource was not part of this forecast. Ask for a new one and it will be");
	}

}
