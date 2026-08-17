package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Somebody offered to cut a piece of work the forecast was never about.
 *
 * <p>
 * A run holds the plan exactly as it was when it was made, and that is the plan a
 * counterfactual is a counterfactual of. Work added since was not in it and cannot be
 * taken out of it; work archived since was, and still can. Evaluating a candidate the run
 * never held would silently answer a question about a different plan — and answer it with
 * the baseline, since cutting nothing changes nothing, which reads as "this buys you
 * nothing" rather than as "this is not what you think it is".
 *
 * <p>
 * A fact about what the request names, so it is decided before anything is simulated —
 * and a bad request rather than a conflict, for the reason
 * {@code dependency_across_projects} is.
 */
public class CandidateNotInForecastException extends ApiProblemException {

	public CandidateNotInForecastException() {
		super(HttpStatus.BAD_REQUEST, "Candidate not in forecast", "candidate_not_in_forecast",
				"That work was not in the plan this forecast was made of, so it cannot be cut from it");
	}

}
