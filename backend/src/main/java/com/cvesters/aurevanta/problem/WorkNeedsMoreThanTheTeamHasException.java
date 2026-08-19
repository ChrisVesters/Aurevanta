package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Something in the plan needs more of a pool than the team now has, so it can never
 * start.
 *
 * <p>
 * <strong>The other end of {@link RequirementExceedsPoolException}, and it exists because
 * barring one door is not the same as closing the room.</strong> That one refuses a
 * requirement larger than the pool at the moment somebody writes it; this one catches the
 * state arrived at from the other side — a pool shrunk below what work already depends on
 * — which no check on the requirement can see, and which no check on the pool may make
 * either, since {@code resource} knowing about {@code requirement} would point an arrow
 * back the way it came.
 *
 * <p>
 * <strong>Refused rather than dropped, unlike a need on a pool the team has put
 * away.</strong> Leaving it out would make the work generic — schedulable against
 * whatever is free — and the forecast would come back sooner than the plan can possibly
 * be delivered, with nothing on screen looking amiss. That is this product's own failure
 * mode, and a limitation beside the date would not undo it.
 *
 * <p>
 * Nothing is written, for {@link NothingToForecastException}'s reason: a refusal that
 * stored a run would leave the history holding a forecast nobody received.
 */
public class WorkNeedsMoreThanTheTeamHasException extends ApiProblemException {

	public WorkNeedsMoreThanTheTeamHasException() {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "Work needs more than the team has", "work_needs_more_than_the_team_has",
				"Some work in this plan needs more of a resource than the team has, so it could never start");
	}

}
