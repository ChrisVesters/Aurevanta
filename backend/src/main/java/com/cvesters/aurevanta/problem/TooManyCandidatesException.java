package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * More things to consider cutting than there is time to consider them in.
 *
 * <p>
 * <strong>Every candidate is a whole simulation.</strong> There is no closed form for
 * what cutting something buys — the aggregator is a scheduler, and an item off the
 * deciding path buys nothing however large it is — so the only way to find out is to run
 * the plan again without it. The bound is on the number of runs that costs, and it is
 * stated rather than discovered as a timeout.
 *
 * <p>
 * Refused rather than quietly truncated. A list silently cut to the first twelve would
 * answer confidently about a set nobody chose, and the thirteenth might have been the one
 * worth dropping.
 */
public class TooManyCandidatesException extends ApiProblemException {

	public TooManyCandidatesException(int most) {
		super(HttpStatus.BAD_REQUEST, "Too many candidates", "too_many_candidates",
				"Each thing considered costs a whole simulation, so at most " + most + " may be weighed at once");
	}

}
