package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A forecast named a capacity for an organisation that has described its team.
 *
 * <p>
 * <strong>Refused rather than ignored</strong>, which is the rule
 * {@link ProgressNotApplicableException} states: silently dropping input is worse than
 * refusing it, because the person is not told they have been overruled. Once there are
 * pools the concurrency is what they hold, and a second number beside them would leave a
 * reader unable to tell which one bound the answer.
 */
public class CapacityNotApplicableException extends ApiProblemException {

	public CapacityNotApplicableException() {
		super(HttpStatus.BAD_REQUEST, "Capacity not applicable", "capacity_not_applicable",
				"This organisation's resources say how much can be under way at once, so a capacity cannot be named");
	}

}
