package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The caller says a range was collected a way this server has never collected one.
 *
 * <p>
 * A refusal of its own rather than a validation error, because it is not a constraint on
 * the shape of a field — the value is a perfectly good string, and what is wrong is that
 * it names nothing. A {@code @Pattern} would degrade to a generic {@code invalid} and
 * tell somebody the box is malformed when the box is fine.
 *
 * <p>
 * Worth refusing rather than storing and shrugging at. The column exists so that
 * calibration can ask whether changing the question changed how often a band contained
 * the truth, which is the only evidence elicitation's own claim can ever have — and an
 * answer partitioned by a value nobody ever collected under is worse than no partition,
 * because it looks like data.
 */
public class UnknownElicitationMethodException extends ApiProblemException {

	public UnknownElicitationMethodException() {
		super(HttpStatus.BAD_REQUEST, "Unknown elicitation method", "unknown_elicitation_method",
				"That is not a way this server records an estimate as having been asked for");
	}

}
