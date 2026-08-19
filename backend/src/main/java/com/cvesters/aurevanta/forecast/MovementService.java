package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedItem;
import com.cvesters.aurevanta.forecast.Movement.Step;
import com.cvesters.aurevanta.forecast.model.Comparison;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ForecastTerms;
import com.cvesters.aurevanta.forecast.model.RunObserver;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
import com.cvesters.aurevanta.problem.ForecastNotComparableException;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;
import com.cvesters.aurevanta.problem.NotAMemberException;

/**
 * Why the date moved, between two forecasts of one plan.
 *
 * <p>
 * <strong>The terms sum to the whole distance and not to most of it</strong>, because
 * each is measured with every earlier one already applied and the final state <em>is</em>
 * the newer run — seed included. {@link Movement} carries the argument and the order.
 *
 * <p>
 * <strong>Six simulations, not eight.</strong> Only the steps that change the model's
 * inputs need re-running: the calendar and the start date move the date without touching
 * an hour, so the last two terms are read off the same hours as the one before them. What
 * that leaves is one replay per model-changing state, plus one to check the older run
 * still reproduces — because a decomposition whose baseline is a run the engine no longer
 * makes is an exact account of a movement that never happened.
 */
@Service
public class MovementService {

	private final ForecastService forecasts;

	MovementService(ForecastService forecasts) {
		this.forecasts = forecasts;
	}

	/**
	 * What moved the date between two runs of one plan, oldest first whichever order they
	 * were named in.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ForecastNotComparableException if the two are of different plans, or were
	 * made by different versions of the engine
	 * @throws ForecastReplayMismatchException if either run no longer reproduces
	 */
	@Transactional(readOnly = true)
	public MovementResponse between(UUID callerId, UUID tenantId, UUID oneRunId, UUID otherRunId) {
		ForecastRun a = this.forecasts.get(callerId, tenantId, oneRunId);
		ForecastRun b = this.forecasts.get(callerId, tenantId, otherRunId);
		if (!a.getProject().getId().equals(b.getProject().getId())) {
			throw new ForecastNotComparableException();
		}
		// Ordered here rather than demanded of the caller: which of two runs is older is
		// a
		// fact about the rows, and a refusal for naming them the wrong way round would be
		// one
		// nobody could act on without looking it up.
		ForecastRun older = a.getCreatedAt().isAfter(b.getCreatedAt()) ? b : a;
		ForecastRun newer = (older == a) ? b : a;
		if (!Comparison.between(ForecastService.termsOf(older), ForecastService.termsOf(newer)).comparable()) {
			throw new ForecastNotComparableException();
		}
		return account(older, newer);
	}

	/**
	 * The five states the plan passes through, and the dates read off each.
	 *
	 * <p>
	 * The last of them is the newer run itself, which is what {@code requireReproduces}
	 * then checks — and checking it is what says the terms below add up to the distance
	 * between two stored answers rather than to the distance between two things this
	 * method invented.
	 */
	private MovementResponse account(ForecastRun older, ForecastRun newer) {
		ForecastInputs olderInputs = this.forecasts.inputsOf(older);
		ForecastInputs newerInputs = this.forecasts.inputsOf(newer);
		ForecastTerms olderTerms = ForecastService.termsOf(older);
		ForecastTerms newerTerms = ForecastService.termsOf(newer);

		// The older run under its own seed, so a baseline that has drifted is refused
		// rather
		// than absorbed into the sampling term where it would look like an ordinary
		// reading.
		ForecastService.requireReproduces(older, run(olderInputs, olderTerms, older.getSampleCount(), older.getSeed()));

		int samples = newer.getSampleCount();
		long seed = newer.getSeed();
		Forecast sampled = run(olderInputs, olderTerms, samples, seed);
		Forecast progressed = run(withProgress(olderInputs, newerInputs), olderTerms, samples, seed);
		Forecast reEstimated = run(withEstimates(withProgress(olderInputs, newerInputs), newerInputs), olderTerms,
				samples, seed);
		Forecast rescoped = run(newerInputs, olderTerms, samples, seed);
		Forecast reasked = run(newerInputs, newerTerms, samples, seed);
		// Identical inputs, identical assumptions, identical seed: this *is* the newer
		// run.
		ForecastService.requireReproduces(newer, reasked);

		List<Forecast> states = List.of(sampled, progressed, reEstimated, rescoped, reasked);
		List<MovementAtResponse> at = new ArrayList<>(ForecastService.CONFIDENCES.length);
		for (int confidence : ForecastService.CONFIDENCES) {
			at.add(read(older, olderTerms, newerTerms, confidence, states));
		}
		return new MovementResponse(older.getId(), newer.getId(), Movement.RULE, 6, at);
	}

	/**
	 * One confidence's account: the hours after each state, turned into dates under
	 * whichever calendar that state had, and differenced.
	 *
	 * <p>
	 * <strong>Days are the difference between two dates and never the hours
	 * converted.</strong> Each end is rounded up to a whole day on its own, so a day
	 * count derived from an hours difference disagrees with the dates on screen about
	 * half the time — which is M4's warning about a step function, met from a new
	 * direction.
	 */
	private static MovementAtResponse read(ForecastRun older, ForecastTerms olderTerms, ForecastTerms newerTerms,
			int confidence, List<Forecast> states) {
		// The two ends of one percentile are read from different places on purpose: the
		// older run's from the row it stored, the states' from what the engine just
		// produced. A decomposition that took its baseline from a replay would sum to the
		// distance between two things this method computed rather than between two
		// answers somebody was given.
		BigDecimal from = older.hoursAt(confidence);
		List<BigDecimal> after = new ArrayList<>();
		for (Forecast state : states) {
			after.add(ForecastRun.hours(replayedHours(state, confidence)));
		}
		// Every state up to the assumptions is read under the older run's calendar; the
		// last
		// two terms change nothing but how the same hours are read.
		List<BigDecimal> hoursByStep = List.of(from, after.get(0), after.get(1), after.get(2), after.get(3),
				after.get(4), after.get(4), after.get(4));
		List<ForecastTerms> termsByStep = List.of(olderTerms, olderTerms, olderTerms, olderTerms, olderTerms,
				olderTerms, calendarOf(newerTerms, olderTerms), newerTerms);

		List<MovementTermResponse> terms = new ArrayList<>(Movement.ORDER.size());
		for (int step = 0; step < Movement.ORDER.size(); step++) {
			BigDecimal was = hoursByStep.get(step);
			BigDecimal now = hoursByStep.get(step + 1);
			terms.add(new MovementTermResponse(Movement.ORDER.get(step), now.subtract(was),
					daysBetween(termsByStep.get(step), was, termsByStep.get(step + 1), now)));
		}
		return new MovementAtResponse(confidence, dateUnder(olderTerms, from), dateUnder(newerTerms, after.get(4)),
				from, after.get(4), terms);
	}

	/**
	 * The newer calendar over the older start, which is the state the calendar step lands
	 * in.
	 */
	private static ForecastTerms calendarOf(ForecastTerms calendar, ForecastTerms rest) {
		return new ForecastTerms(rest.engineVersion(), rest.priorityRule(), calendar.calendarRule(),
				calendar.workingHoursPerDay(), rest.capacity(), rest.resourcing(), rest.teamFactorWorseByPercent(),
				rest.scopeGrowthP10Percent(), rest.scopeGrowthP90Percent(), rest.startsOn());
	}

	private static Integer daysBetween(ForecastTerms was, BigDecimal wasHours, ForecastTerms now, BigDecimal nowHours) {
		LocalDate before = dateUnder(was, wasHours);
		LocalDate after = dateUnder(now, nowHours);
		return (before == null || after == null) ? null : (int) ChronoUnit.DAYS.between(before, after);
	}

	/**
	 * The day a state's hours land on, or null when that state has no calendar to read
	 * them through — a run made before M4 has none, and inventing one would be the claim
	 * `V14` deliberately did not backfill.
	 */
	private static LocalDate dateUnder(ForecastTerms terms, BigDecimal hours) {
		if (terms.startsOn() == null || terms.workingHoursPerDay() == null
				|| !WorkingCalendar.RULE.equals(terms.calendarRule())) {
			return null;
		}
		return WorkingCalendar.finishOn(terms.startsOn(), hours, terms.workingHoursPerDay());
	}

	private Forecast run(ForecastInputs inputs, ForecastTerms terms, int samples, long seed) {
		return ForecastService.replayWith(inputs.toModels(), inputs, terms, samples, seed, RunObserver.NONE);
	}

	/** What each item's status and spent hours became, for the work both runs hold. */
	private static ForecastInputs withProgress(ForecastInputs older, ForecastInputs newer) {
		Map<UUID, PlannedItem> now = byId(newer);
		List<PlannedItem> items = new ArrayList<>(older.items().size());
		for (PlannedItem was : older.items()) {
			PlannedItem is = now.get(was.id());
			items.add((is == null) ? was : new PlannedItem(was.id(), is.status(), is.spentHours(), was.estimates()));
		}
		return new ForecastInputs(items, older.edges());
	}

	/** And what everybody now thinks it will take, for that same work. */
	private static ForecastInputs withEstimates(ForecastInputs partway, ForecastInputs newer) {
		Map<UUID, PlannedItem> now = byId(newer);
		List<PlannedItem> items = new ArrayList<>(partway.items().size());
		for (PlannedItem was : partway.items()) {
			PlannedItem is = now.get(was.id());
			items.add((is == null) ? was : new PlannedItem(was.id(), was.status(), was.spentHours(), is.estimates()));
		}
		return new ForecastInputs(items, partway.edges());
	}

	private static Map<UUID, PlannedItem> byId(ForecastInputs inputs) {
		Map<UUID, PlannedItem> found = new HashMap<>();
		for (PlannedItem item : inputs.items()) {
			found.put(item.id(), item);
		}
		return found;
	}

	private static double replayedHours(Forecast forecast, int confidence) {
		return switch (confidence) {
			case 50 -> forecast.p50Hours();
			case 80 -> forecast.p80Hours();
			default -> forecast.p95Hours();
		};
	}

}
