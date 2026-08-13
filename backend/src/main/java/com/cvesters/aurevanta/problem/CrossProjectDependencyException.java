package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The two ends of a dependency are in different plans.
 *
 * <p>
 * Refused rather than allowed, because a forecast is taken over one plan: an edge leaving
 * it would be a constraint the scheduler could not see, and the plan it belongs to could
 * not be answered for. A dependency row carries a single {@code project_id} for exactly
 * this reason — there is no honest value for it when its ends disagree.
 *
 * <p>
 * Reachable only within one organisation. An item in somebody else's answers
 * {@link WorkItemNotFoundException} first, so this never says whether an identifier from
 * outside exists.
 */
public class CrossProjectDependencyException extends ApiProblemException {

	public CrossProjectDependencyException() {
		super(HttpStatus.BAD_REQUEST, "Dependency across projects", "dependency_across_projects",
				"Both ends of a dependency have to be in the same plan");
	}

}
