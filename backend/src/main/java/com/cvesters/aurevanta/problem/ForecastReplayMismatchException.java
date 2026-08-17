package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A stored run was replayed and did not come out the same, so nothing is said about it.
 *
 * <p>
 * <strong>This is the persistence test promoted out of the suite and into the
 * product.</strong> A run can be explained only by the engine that made it, and a run
 * stores the six figures it produced — so replaying it and comparing costs six
 * comparisons and answers the only question that matters: <em>does this still come out
 * the same?</em> It catches an engine version bumped for a change that cannot be reduced
 * to a parameter, a JDK generator whose Gaussian moved, a snapshot format that drifted,
 * and an accidental edit to the sampler, without any of them having to be anticipated or
 * listed.
 *
 * <p>
 * <strong>Refusing is the whole point, and the alternative is worse than
 * useless.</strong> A ranking produced by a different model is not a less precise ranking
 * of this plan, it is an exact ranking of a plan nobody forecast — and it would look
 * entirely reasonable, because a list of items with numbers beside them always does. That
 * is this product's own failure mode, and it is the reason there is nothing in the body
 * but the code.
 *
 * <p>
 * A conflict rather than a bad request: nothing about the call is wrong, and what
 * disagrees is the stored run and the engine reading it. The same distinction
 * {@code dependency_cycle} draws from {@code self_dependency}.
 */
public class ForecastReplayMismatchException extends ApiProblemException {

	public ForecastReplayMismatchException() {
		super(HttpStatus.CONFLICT, "Forecast cannot be replayed", "forecast_replay_mismatch",
				"This run was made by an engine that no longer produces the same numbers, so it cannot be explained");
	}

}
