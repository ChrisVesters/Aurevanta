package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A piece of work was said to need more of a pool than that pool holds.
 *
 * <p>
 * <strong>Refused where it is typed rather than left to the scheduler</strong>, which is
 * a departure from the rule beside it: an archived pool may still be named, because the
 * forecast can leave that requirement out and say so. This one it cannot. Work needing
 * three of a pool of two never starts, at any moment of any run, so a plan holding it has
 * no schedule at all — and the alternative to refusing is a number nobody can produce.
 *
 * <p>
 * The remedy is on screen already: every box carries how many the pool has, so the
 * refusal names a bound the person can see rather than one they have to go and look up.
 */
public class RequirementExceedsPoolException extends ApiProblemException {

	public RequirementExceedsPoolException() {
		super(HttpStatus.CONFLICT, "Requirement exceeds pool", "requirement_exceeds_pool",
				"A piece of work cannot need more of a resource than that resource has");
	}

}
