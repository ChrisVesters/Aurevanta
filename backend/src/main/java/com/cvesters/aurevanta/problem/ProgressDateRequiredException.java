package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A state was claimed without the date that makes it evidence: work in progress with no
 * start, or work finished with no completion.
 *
 * <p>
 * A refusal rather than a server-supplied default, which is the decision worth recording.
 * Stamping "now" would be easy and would quietly invent the one thing this row exists to
 * hold — M10's burn-up and M8's calibration both read these dates, and neither can tell a
 * date somebody reported from a date the server guessed while nobody was looking.
 *
 * <p>
 * Not a per-field complaint, for the same reason as {@link EstimateOutOfOrderException}:
 * which field is missing depends on the state in another field, so pointing at one of
 * them describes only half of what is wrong.
 */
public class ProgressDateRequiredException extends ApiProblemException {

	public ProgressDateRequiredException() {
		super(HttpStatus.BAD_REQUEST, "Progress date required", "progress_date_required",
				"Work in progress needs a start date, and finished work needs a completion date");
	}

}
