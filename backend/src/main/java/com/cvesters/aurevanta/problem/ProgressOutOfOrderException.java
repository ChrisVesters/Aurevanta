package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Something finished before it began.
 *
 * <p>
 * Refused rather than stored, on the same grounds as an estimate whose ends are the wrong
 * way round: it is a typo, and the features that read these dates cannot tell a typo from
 * a fact. A burn-up drawn through it would show work completing before it was picked up.
 */
public class ProgressOutOfOrderException extends ApiProblemException {

	public ProgressOutOfOrderException() {
		super(HttpStatus.BAD_REQUEST, "Progress out of order", "progress_out_of_order",
				"Work cannot be finished before it was started");
	}

}
