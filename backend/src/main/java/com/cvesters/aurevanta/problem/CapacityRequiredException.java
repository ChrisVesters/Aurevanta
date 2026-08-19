package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A forecast was asked for without saying how much can be under way at once, by an
 * organisation that has not described its team either.
 *
 * <p>
 * <strong>One of the two has to be said and neither may be guessed.</strong> A plan
 * forecast with no bound on concurrency assumes everybody is available for everything,
 * and is optimistic by the same margin summing durations is pessimistic — the same ten
 * items came out at 51 or 86 days depending on nothing else. A server that filled this in
 * would leave the caller holding a claim about their team they never made.
 */
public class CapacityRequiredException extends ApiProblemException {

	public CapacityRequiredException() {
		super(HttpStatus.BAD_REQUEST, "Capacity required", "capacity_required",
				"Say how much can be under way at once, or describe the team's resources");
	}

}
