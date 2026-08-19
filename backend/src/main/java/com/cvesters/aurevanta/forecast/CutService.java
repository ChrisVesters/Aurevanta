package com.cvesters.aurevanta.forecast;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.forecast.model.ConfidenceBy;
import com.cvesters.aurevanta.forecast.model.ItemModel;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.problem.CandidateNotInForecastException;
import com.cvesters.aurevanta.problem.ForecastHasNoCalendarException;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.TooManyCandidatesException;

/**
 * What to cut to hit a date at a confidence, and what each cut would buy — the inverse
 * query.
 *
 * <p>
 * <strong>It weighs and never decides.</strong> Nothing here is written and nothing is
 * archived: acting on the answer means putting work away on the plan screen, where
 * somebody can see what else it is connected to.
 *
 * <p>
 * <strong>The caller names the candidates and this proposes none.</strong> Which work is
 * negotiable is a judgement about value and nothing in this schema records any — a
 * four-week task a regulator requires is not a candidate and a two-day nicety is. It also
 * bounds the cost honestly, since every candidate is a whole simulation.
 */
@Service
class CutService {

	private final ForecastService forecasts;

	private final PlanTitles titles;

	CutService(ForecastService forecasts, PlanTitles titles) {
		this.forecasts = forecasts;
		this.titles = titles;
	}

	/**
	 * What each of a named set of cuts would buy against a date, largest first.
	 *
	 * <p>
	 * <strong>Every candidate is a whole simulation, and there is no way round
	 * it.</strong> The aggregator is a scheduler, so what cutting something buys depends
	 * on whether it was ever on the path that decided the finish — an item off it buys
	 * nothing however large it is, and no arithmetic over one item's estimate can say
	 * which case it is in.
	 *
	 * <p>
	 * <strong>Every run of every candidate uses the same random numbers as the
	 * baseline.</strong> A cut item still takes its draws and is worth nothing, so the
	 * two sides are paired and the difference between them is the cut rather than the
	 * luck. Measured, that halves the noise: the same plan re-seeded moves the answer by
	 * more than a point at ten thousand runs, and a cut worth having buys about five.
	 *
	 * <p>
	 * <strong>What comes back is what each buys <em>on its own</em>, and they do not
	 * add.</strong> Two cuts on one chain overlap; two on separate branches leave the
	 * later one deciding. The set that reaches a bar has to be searched for and measured,
	 * which is a different question from this one.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ForecastNotFoundException if no run in it has that identifier
	 * @throws TooManyCandidatesException if more work is offered than a request may weigh
	 * @throws ForecastHasNoCalendarException if the run was made before there was one
	 * @throws CandidateNotInForecastException if the run was never about that work
	 * @throws ForecastReplayMismatchException if replaying it does not reproduce it
	 */
	@Transactional(readOnly = true)
	public CutOptionsResponse cutsFor(UUID callerId, UUID tenantId, UUID runId, LocalDate by, int confidence,
			List<UUID> candidates) {
		// A fact about the request alone, answered before anything is looked up or run.
		if (candidates.size() > CutsRequest.MOST_CANDIDATES) {
			throw new TooManyCandidatesException(CutsRequest.MOST_CANDIDATES);
		}
		ForecastRun run = this.forecasts.get(callerId, tenantId, runId);
		if (!run.hasReadableCalendar()) {
			throw new ForecastHasNoCalendarException();
		}
		ForecastInputs inputs = this.forecasts.inputsOf(run);
		List<Integer> cutting = positionsOf(candidates, inputs);
		BigDecimal budget = WorkingCalendar.hoursBy(run.getStartsOn(), by, run.getWorkingHoursPerDay());

		List<ItemModel> plan = inputs.toModels();
		ConfidenceBy baseline = new ConfidenceBy(budget.doubleValue());
		// One replay does two jobs: it establishes the baseline every cut is measured
		// against, and it proves this engine still reproduces the run it is about to give
		// advice on.
		ForecastReplays.requireReproduces(run, ForecastReplays.replay(run, inputs, plan, baseline));

		double stands = percent(baseline);

		Map<UUID, WorkItem> named = this.titles.in(callerId, tenantId, run.getProject().getId());
		// Round one of the search and the answer to "what is each worth on its own" are
		// one
		// set of simulations, so they are run once and read twice.
		Map<Integer, Double> alone = new HashMap<>();
		List<CutResponse> weighed = new ArrayList<>(cutting.size());
		for (int at : cutting) {
			double reached = confidenceWithout(run, inputs, plan, List.of(at), budget);
			alone.put(at, reached);
			UUID itemId = inputs.items().get(at).id();
			WorkItem still = named.get(itemId);
			weighed.add(new CutResponse(itemId, PlanTitles.titleOf(still), PlanTitles.isArchived(still), reached,
					reached - stands, reached >= confidence));
		}
		// Largest first, because the order is the answer — the same reason a contribution
		// ranking is sorted here rather than by whoever draws it.
		weighed.sort(Comparator.comparingDouble(CutResponse::buys).reversed());

		Search search = new Search(run, inputs, plan, budget, confidence, named, cutting, alone, stands);
		CutPlanResponse together = search.run();
		return new CutOptionsResponse(budget, stands, stands >= confidence, search.simulations(), List.copyOf(weighed),
				together);
	}

	/**
	 * The shortest list this will look for: take the best, then the best of what is left
	 * <em>with that one already gone</em>, and stop at the bar.
	 *
	 * <p>
	 * <strong>Greedy, and not optimal.</strong> The best subset needs two-to-the-n
	 * evaluations and each is a whole run of the plan; this needs one per candidate per
	 * step. It can miss a pair that only works together — two halves of one feature,
	 * neither of which shortens the path alone — and the answer says it is <em>a</em>
	 * list rather than the shortest.
	 *
	 * <p>
	 * <strong>Nothing here is added up.</strong> Every figure reported is a plan run with
	 * all the chosen cuts applied at once, because two cuts do not buy the total of what
	 * each buys: on one chain they overlap, and on separate branches the finish is the
	 * later of the two and shortening one leaves the other deciding.
	 */
	private static final class Search {

		private final ForecastRun run;

		private final ForecastInputs inputs;

		private final List<ItemModel> plan;

		private final BigDecimal budget;

		private final int confidence;

		private final Map<UUID, WorkItem> named;

		private final List<Integer> remaining;

		private final List<Integer> chosen = new ArrayList<>();

		/**
		 * What each remaining candidate reaches, with everything already chosen also cut.
		 * Null between rounds, which is what says the last round's figures have gone
		 * stale.
		 */
		private Map<Integer, Double> measured;

		private double reached;

		private int simulations;

		private Search(ForecastRun run, ForecastInputs inputs, List<ItemModel> plan, BigDecimal budget, int confidence,
				Map<UUID, WorkItem> named, List<Integer> cutting, Map<Integer, Double> alone, double baseline) {
			this.run = run;
			this.inputs = inputs;
			this.plan = plan;
			this.budget = budget;
			this.confidence = confidence;
			this.named = named;
			this.remaining = new ArrayList<>(cutting);
			// Round one is what each candidate is worth alone, which has already been
			// run.
			this.measured = alone;
			this.reached = baseline;
			this.simulations = cutting.size() + 1;
		}

		private CutPlanResponse run() {
			List<CutStepResponse> steps = new ArrayList<>();
			CutSearchEnding ending = CutSearchEnding.MET;
			while (this.reached < this.confidence) {
				if (this.remaining.isEmpty()) {
					ending = CutSearchEnding.NOTHING_LEFT;
					break;
				}
				if (this.measured == null && !weighWhatIsLeft()) {
					ending = CutSearchEnding.BUDGET_SPENT;
					break;
				}
				steps.add(take(best()));
			}
			return new CutPlanResponse(List.copyOf(steps), ending);
		}

		/**
		 * Every candidate still in play, each with everything already chosen also cut.
		 * @return whether there was budget left to do it at all
		 */
		private boolean weighWhatIsLeft() {
			if (this.simulations + this.remaining.size() > CutsRequest.MOST_SIMULATIONS) {
				return false;
			}
			this.measured = new HashMap<>();
			for (int at : this.remaining) {
				List<Integer> together = new ArrayList<>(this.chosen);
				together.add(at);
				this.measured.put(at, confidenceWithout(this.run, this.inputs, this.plan, together, this.budget));
				this.simulations++;
			}
			return true;
		}

		private int best() {
			int best = this.remaining.getFirst();
			for (int at : this.remaining) {
				if (this.measured.get(at) > this.measured.get(best)) {
					best = at;
				}
			}
			return best;
		}

		private CutStepResponse take(int at) {
			this.reached = this.measured.get(at);
			this.chosen.add(at);
			this.remaining.remove(Integer.valueOf(at));
			// The next round is measured afresh: what a candidate is worth changes once
			// something else has gone, which is the whole reason this is a search rather
			// than a sort.
			this.measured = null;
			UUID itemId = this.inputs.items().get(at).id();
			WorkItem still = this.named.get(itemId);
			return new CutStepResponse(itemId, PlanTitles.titleOf(still), PlanTitles.isArchived(still), this.reached);
		}

		private int simulations() {
			return this.simulations;
		}

	}

	/**
	 * The plan run again with a set of work imagined away, and how often it beat the
	 * date.
	 */
	private static double confidenceWithout(ForecastRun run, ForecastInputs inputs, List<ItemModel> plan,
			List<Integer> cutting, BigDecimal budget) {
		List<ItemModel> without = new ArrayList<>(plan);
		for (int at : cutting) {
			without.set(at, without.get(at).asCut());
		}
		ConfidenceBy counted = new ConfidenceBy(budget.doubleValue());
		ForecastReplays.replay(run, inputs, without, counted);
		return percent(counted);
	}

	/**
	 * Where each candidate sits in the plan the run was made of.
	 *
	 * <p>
	 * Resolved before anything is simulated, because a candidate the run never held is a
	 * fact about the request — and because evaluating it would otherwise answer the
	 * question with the baseline, which reads as "this buys you nothing" rather than as
	 * "this is not what you think it is".
	 *
	 * <p>
	 * <strong>The same work named twice is one candidate.</strong> Nothing is discarded
	 * by that — a second mention asks no second question — where weighing it twice would
	 * spend a simulation to repeat an answer, put the same row in the ranking twice, and
	 * let the greedy search below "cut" one item at two of its steps. The screen cannot
	 * produce a duplicate, since candidates are ticked rather than typed; the API is the
	 * contract this holds up. The count is still taken over what the request named,
	 * because that is what the caller has to shorten.
	 */
	private static List<Integer> positionsOf(List<UUID> candidates, ForecastInputs inputs) {
		Map<UUID, Integer> position = new HashMap<>();
		for (int at = 0; at < inputs.items().size(); at++) {
			position.put(inputs.items().get(at).id(), at);
		}
		List<Integer> cutting = new ArrayList<>(candidates.size());
		for (UUID candidate : candidates) {
			Integer at = position.get(candidate);
			if (at == null) {
				throw new CandidateNotInForecastException();
			}
			if (!cutting.contains(at)) {
				cutting.add(at);
			}
		}
		return cutting;
	}

	/**
	 * A share of runs as the percentage a person typed, converted once at this boundary —
	 * the model deals in shares and nobody asks for a date at 0.85 confidence.
	 */
	private static double percent(ConfidenceBy counted) {
		return counted.share() * 100.0;
	}

}
