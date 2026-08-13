package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The claim carries something its own status has no room for: an effort on work that has
 * not started, or a completion date on work that is still under way.
 *
 * <p>
 * <strong>Refused rather than quietly dropped, and that is the whole point of
 * it.</strong> The obvious alternative — keep what the status allows and discard the rest
 * — was what this endpoint did first, and it is worse than it sounds: somebody types four
 * hours against a task, saves, watches the form close, and the number is gone with
 * nothing said. A request that contradicts itself is not a request with a tidy
 * interpretation; it is two statements, one of which has to be wrong, and only the person
 * who wrote them knows which.
 */
public class ProgressNotApplicableException extends ApiProblemException {

	public ProgressNotApplicableException() {
		super(HttpStatus.BAD_REQUEST, "Progress not applicable", "progress_not_applicable",
				"Work that has not started records no dates or effort, and work in progress has no completion date");
	}

}
