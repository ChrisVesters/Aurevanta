package com.cvesters.aurevanta.forecast;

import com.cvesters.aurevanta.resource.ResourceService;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ItemModel;
import com.cvesters.aurevanta.forecast.model.RunObserver;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
import com.cvesters.aurevanta.problem.ForecastHasNoCalendarException;
import com.cvesters.aurevanta.problem.ForecastHasNoResourcesException;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ResourceNotInForecastException;

/**
 * What another unit of one pool would buy against a stored run — the resource model.
 *
 * <p>
 * <strong>The pairing is exact for free</strong>, unlike a cut, which has to take a draw
 * and discard it to keep the stream aligned: units change what may <em>start</em> and
 * never what is sampled, so the counterfactual and the baseline draw the same numbers in
 * the same order from the same seed.
 *
 * <p>
 * <strong>Hiring is weighed and never decided.</strong> Nothing is written. What a person
 * costs and how long they take to be useful are facts this server does not have.
 */
@Service
class HireService {

	private final ForecastService forecasts;

	private final ResourceService resources;

	HireService(ForecastService forecasts, ResourceService resources) {
		this.forecasts = forecasts;
		this.resources = resources;
	}

	/**
	 * What adding to one pool would be worth, measured against a stored run.
	 *
	 * <p>
	 * <strong>the cut search's machinery answering `roadmap.md`'s most compelling
	 * question.</strong> Every counterfactual is a replay of the stored run from its own
	 * seed with one pool larger — nothing is written, and forty simulations could go past
	 * without {@code forecast_runs} gaining a row.
	 *
	 * <p>
	 * <strong>The pairing is exact for free here, where a cut had to work for
	 * it.</strong> a cut takes a draw and discards it, because an item that took no draw
	 * would shift every later number in the run and turn the measurement into noise.
	 * Units change what may <em>start</em> and never what is sampled, so the two runs
	 * being compared draw the same numbers in the same order without anything being
	 * arranged.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ForecastNotFoundException if no run in it has that identifier
	 * @throws ForecastHasNoResourcesException if the run was scheduled against a capacity
	 * @throws ResourceNotFoundException if the pool is not this organisation's
	 * @throws ResourceNotInForecastException if the run was not scheduled against it
	 * @throws ForecastHasNoCalendarException if the run has no calendar to read a date
	 * through
	 * @throws ForecastReplayMismatchException if the run no longer reproduces
	 */
	@Transactional(readOnly = true)
	public HireOptionsResponse hiresFor(UUID callerId, UUID tenantId, UUID runId, UUID resourceId, int units) {
		ForecastRun run = this.forecasts.get(callerId, tenantId, runId);
		if (!run.hasReadableCalendar()) {
			throw new ForecastHasNoCalendarException();
		}
		// Looked up so that a pool belonging to somebody else is not there at all, rather
		// than being reported as one this run did not use.
		this.resources.get(callerId, tenantId, resourceId);
		ForecastInputs inputs = this.forecasts.inputsOf(run);
		if (inputs.pools().isEmpty()) {
			throw new ForecastHasNoResourcesException();
		}
		if (inputs.pools().stream().noneMatch((pool) -> pool.resourceId().equals(resourceId))) {
			throw new ResourceNotInForecastException();
		}

		List<ItemModel> plan = inputs.toModels();
		// The baseline does two jobs, as the inverse query's does: it is what every row
		// below is
		// measured
		// against, and it proves this engine still reproduces the run it is about to give
		// advice on.
		Forecast stands = ForecastReplays.replay(run, inputs, plan, RunObserver.NONE);
		ForecastReplays.requireReproduces(run, stands);

		List<List<HireStepResponse>> steps = new ArrayList<>(ForecastService.CONFIDENCES.length);
		for (int at = 0; at < ForecastService.CONFIDENCES.length; at++) {
			steps.add(new ArrayList<>(units));
		}
		for (int extra = 1; extra <= units; extra++) {
			// Cumulative and measured rather than the first row multiplied: the second
			// person is worth less than the first, and how much less is the answer.
			ForecastInputs larger = inputs.withMore(resourceId, extra);
			Forecast with = ForecastReplays.replay(run, larger, plan, RunObserver.NONE);
			for (int at = 0; at < ForecastService.CONFIDENCES.length; at++) {
				steps.get(at).add(step(run, extra, stands, with, ForecastService.CONFIDENCES[at]));
			}
		}

		List<HireAtResponse> at = new ArrayList<>(ForecastService.CONFIDENCES.length);
		for (int which = 0; which < ForecastService.CONFIDENCES.length; which++) {
			at.add(new HireAtResponse(ForecastService.CONFIDENCES[which],
					dateOf(run, stands, ForecastService.CONFIDENCES[which]), List.copyOf(steps.get(which))));
		}
		return new HireOptionsResponse(resourceId, units + 1, at);
	}

	/**
	 * One row: the date that many extra units buys, and how much sooner it is.
	 *
	 * <p>
	 * Days are the difference between two <em>dates</em> and never hours converted, which
	 * is the calendar's step function met from the same direction the reporting layer's
	 * decomposition met it: each end is rounded up to a whole day on its own.
	 */
	private static HireStepResponse step(ForecastRun run, int units, Forecast stands, Forecast with, int confidence) {
		LocalDate was = dateOf(run, stands, confidence);
		LocalDate now = dateOf(run, with, confidence);
		return new HireStepResponse(units, now, (int) ChronoUnit.DAYS.between(now, was));
	}

	/** One percentile of a replayed forecast, as a day under the run's own calendar. */
	private static LocalDate dateOf(ForecastRun run, Forecast forecast, int confidence) {
		double hours = switch (confidence) {
			case 50 -> forecast.p50Hours();
			case 80 -> forecast.p80Hours();
			default -> forecast.p95Hours();
		};
		return WorkingCalendar.finishOn(run.getStartsOn(), ForecastRun.hours(hours), run.getWorkingHoursPerDay());
	}

}
