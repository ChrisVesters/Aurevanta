package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Somebody asked what a plan's history says as of a day before some of that history
 * happened.
 *
 * <p>
 * Refused rather than answered, on the same grounds as {@code progress_out_of_order}: the
 * two dates disagree about which came first, and nothing downstream could tell that from
 * a team that delivers ahead of time. Bucketing a completion into a week that has not
 * happened yet would give a history whose last week is before its own last delivery, and
 * every number read off it would be wrong in a way nobody could see.
 *
 * <p>
 * A fact about the request alone, so it is answered before any membership or plan is
 * looked up — as an estimate's ordering is, and for the same reason.
 */
public class ThroughputOutOfOrderException extends ApiProblemException {

	public ThroughputOutOfOrderException() {
		super(HttpStatus.BAD_REQUEST, "Throughput out of order", "throughput_out_of_order",
				"Work cannot have been finished after the day being asked about");
	}

}
