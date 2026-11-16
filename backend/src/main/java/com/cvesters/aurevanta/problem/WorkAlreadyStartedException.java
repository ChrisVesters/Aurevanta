package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * An estimate was written against work that is already under way, or finished.
 *
 * <p>
 * <strong>An estimate is a forecast, and a forecast made after the fact is not
 * one.</strong> Once somebody has started a task they know things about it that nobody
 * knew when it was only a line in a plan, and a range written from that knowledge
 * measures hindsight rather than judgement. Every question this product asks of an
 * estimate — how often a band contained the truth, whether the way it was asked for
 * changed anything — is a question about what somebody said <em>before</em> they found
 * out.
 *
 * <p>
 * A conflict rather than a bad request, because nothing in the request is wrong: the
 * three numbers are fine and the item exists. What refuses it is the state that item is
 * already in, which is {@link DependencyAlreadyExistsException}'s situation rather than
 * {@link EstimateOutOfOrderException}'s.
 *
 * <p>
 * <strong>This does not replace calibration's own rule and must not be read as doing
 * so.</strong> Progress can be reported back to not-started, which reopens estimating —
 * deliberately, since a task marked started by mistake would otherwise be stuck. What
 * that leaves is exactly the case {@code work_item_progress} exists for: calibration
 * compares an estimate against the <em>earliest start ever reported</em>, off a log
 * nothing rewrites, so a range written after a start that was undone still scores as a
 * report rather than a forecast.
 */
public class WorkAlreadyStartedException extends ApiProblemException {

	public WorkAlreadyStartedException() {
		super(HttpStatus.CONFLICT, "Work already started", "work_already_started",
				"Work that has been started cannot be estimated: an estimate is what somebody said before they found out");
	}

}
