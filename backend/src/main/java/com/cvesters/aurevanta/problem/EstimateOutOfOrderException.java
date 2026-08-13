package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The three points do not go up: P10 must be no more than P50, and P50 no more than P90.
 *
 * <p>
 * A refusal of its own rather than a per-field validation error, because it is not a fact
 * about any one of the three numbers — each is a perfectly good number, and what is wrong
 * is the relationship between them. {@code FieldProblem} exists to say "this box is
 * wrong", and pointing at one box here would be picking a culprit arbitrarily.
 *
 * <p>
 * Worth refusing rather than accepting and letting M3 sort out, because a band whose ends
 * are the wrong way round is not a pessimistic estimate or an optimistic one — it is a
 * typo, and fitting a distribution to it would produce a confident number from nonsense.
 */
public class EstimateOutOfOrderException extends ApiProblemException {

	public EstimateOutOfOrderException() {
		super(HttpStatus.BAD_REQUEST, "Estimate out of order", "estimate_out_of_order",
				"P10 must be no more than P50, and P50 no more than P90");
	}

}
