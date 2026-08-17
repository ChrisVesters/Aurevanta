package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.dependency.Dependency;
import com.cvesters.aurevanta.dependency.DependencyService;
import com.cvesters.aurevanta.estimate.Estimate;
import com.cvesters.aurevanta.estimate.EstimateService;
import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedEdge;
import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedEstimate;
import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedItem;
import com.cvesters.aurevanta.forecast.model.ConfidenceBy;
import com.cvesters.aurevanta.forecast.model.Contributions;
import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.EstimateQuality;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ItemModel;
import com.cvesters.aurevanta.forecast.model.RunObserver;
import com.cvesters.aurevanta.forecast.model.Schedule;
import com.cvesters.aurevanta.forecast.model.ScopeGrowth;
import com.cvesters.aurevanta.forecast.model.TeamFactor;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.CandidateNotInForecastException;
import com.cvesters.aurevanta.problem.ForecastHasNoCalendarException;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NothingToForecastException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.ScopeGrowthOutOfOrderException;
import com.cvesters.aurevanta.problem.TooManyCandidatesException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectService;
import com.cvesters.aurevanta.user.User;

/**
 * Asking the engine a question about a real plan, and keeping the answer.
 *
 * <p>
 * <strong>Any member may forecast</strong>, as any member may do everything else to a
 * plan: roles govern administration only.
 *
 * <p>
 * <strong>Reaches every other feature through its service, never its tables.</strong>
 * {@code forecast} depends on {@code project}, {@code item}, {@code estimate} and
 * {@code dependency}, and none of them depends on it — so whether a caller may see a plan
 * stays one rule in one place, and the arrows keep pointing one way. It costs a handful
 * of membership re-reads per forecast, against three hundred milliseconds of arithmetic,
 * which is a trade not worth thinking about twice.
 *
 * <p>
 * <strong>Nothing here is written twice.</strong> A run is a record of what was said on a
 * date; there is no update and no delete, exactly as with an estimate.
 */
@Service
public class ForecastService {

	private final ForecastRunRepository runs;

	private final ProjectService projects;

	private final WorkItemService items;

	private final EstimateService estimates;

	private final DependencyService dependencies;

	private final MembershipService memberships;

	private final Clock clock;

	ForecastService(ForecastRunRepository runs, ProjectService projects, WorkItemService items,
			EstimateService estimates, DependencyService dependencies, MembershipService memberships, Clock clock) {
		this.runs = runs;
		this.projects = projects;
		this.items = items;
		this.estimates = estimates;
		this.dependencies = dependencies;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Forecasts a plan and writes down what was asked, what was assumed and what came
	 * back.
	 * @param capacity how many items may be under way at once. Required, and no default
	 * exists anywhere: it moves the answer by more than half, and a server that picked
	 * would be making a claim about a team it has never met.
	 * @param sampleCount how many runs to simulate, or null for the ordinary ten thousand
	 * @param teamFactorWorseByPercent how much longer everything takes in a bad stretch.
	 * Required, and zero is a claim rather than an absence of one.
	 * @param scopeGrowthP10Percent and {@code scopeGrowthP90Percent}, the two ends of how
	 * much a plan like this usually grows
	 * @param startsOn the day work begins, stated by the caller because a server cannot
	 * know what day it is where they are
	 * @param workingHoursPerDay what one person's working day holds. Stored beside the
	 * answer with the name of the rule it will be read through, and never used here: the
	 * engine deals in hours from end to end, and a date is one presentation of one
	 * percentile of what it produced.
	 * @throws ScopeGrowthOutOfOrderException if the growth range descends
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 * @throws NothingToForecastException if no work in the plan carries an estimate
	 */
	@Transactional
	public ForecastRun run(UUID callerId, UUID tenantId, UUID projectId, int capacity, Integer sampleCount,
			BigDecimal teamFactorWorseByPercent, BigDecimal scopeGrowthP10Percent, BigDecimal scopeGrowthP90Percent,
			LocalDate startsOn, BigDecimal workingHoursPerDay) {
		// A fact about the request and nothing else, so it is answered before anything is
		// looked up — a caller who sent a range the wrong way round learns nothing about
		// which plans exist by being told so. The same order `estimate_out_of_order`
		// takes.
		if (scopeGrowthP90Percent.compareTo(scopeGrowthP10Percent) < 0) {
			throw new ScopeGrowthOutOfOrderException();
		}
		User caller = this.memberships.requireMember(callerId, tenantId).getUser();
		Project project = this.projects.get(callerId, tenantId, projectId);
		// Archived work is not going to happen, so it is not forecast — and neither are
		// the
		// estimates on it, which is the same rule the plan screen already reads by.
		List<WorkItem> work = this.items.list(callerId, tenantId, projectId, false);
		List<Estimate> current = this.estimates.currentInProject(callerId, tenantId, projectId);
		List<Dependency> arrows = this.dependencies.listInProject(callerId, tenantId, projectId);

		Set<UUID> forecast = new HashSet<>();
		for (WorkItem item : work) {
			forecast.add(item.getId());
		}
		Map<UUID, List<Estimate>> byItem = new HashMap<>();
		for (Estimate estimate : current) {
			byItem.computeIfAbsent(estimate.getWorkItem().getId(), (item) -> new ArrayList<>()).add(estimate);
		}
		if (byItem.isEmpty()) {
			throw new NothingToForecastException();
		}

		List<PlannedItem> planned = new ArrayList<>(work.size());
		for (WorkItem item : work) {
			planned.add(new PlannedItem(item.getId(), item.getStatus(), item.getActualEffortHours(),
					ranges(byItem.getOrDefault(item.getId(), List.of()))));
		}
		List<PlannedEdge> kept = new ArrayList<>(arrows.size());
		for (Dependency arrow : arrows) {
			UUID predecessor = arrow.getPredecessor().getId();
			UUID successor = arrow.getSuccessor().getId();
			// An arrow into work that has been put away cannot be honoured: the far end
			// is
			// never going to finish, so waiting for it would be waiting forever. Dropped,
			// and said out loud below rather than left for somebody to notice in a date.
			if (forecast.contains(predecessor) && forecast.contains(successor)) {
				kept.add(new PlannedEdge(predecessor, successor, arrow.getLagHours()));
			}
		}

		ForecastInputs inputs = new ForecastInputs(planned, kept);
		int runCount = (sampleCount != null) ? sampleCount : Engine.DEFAULT_SAMPLE_COUNT;
		long seed = ThreadLocalRandom.current().nextLong();
		// Percentages a person typed, turned into the two distributions the engine
		// models with. Both refuse what they cannot fit, and neither refusal is
		// reachable from here: the validation above rules out a negative, and the
		// check at the top rules out a range that descends.
		TeamFactor teamFactor = TeamFactor.from(teamFactorWorseByPercent.doubleValue());
		ScopeGrowth scopeGrowth = ScopeGrowth.from(scopeGrowthP10Percent.doubleValue(),
				scopeGrowthP90Percent.doubleValue());
		Forecast answer = Engine.run(inputs.toModels(), inputs.toPrecedences(), capacity, teamFactor, scopeGrowth,
				runCount, seed);
		ForecastOutputs outputs = new ForecastOutputs(answer.histogram(),
				limitations(planned, kept.size() < arrows.size()));
		// The calendar is written down and not used: nothing about a working day reaches
		// the engine, which is what keeps a calendar change from being a model change.
		// The
		// rule's name goes with it so the run resolves under the calendar it was made
		// with
		// rather than the one this code has by the time somebody reads it.
		return this.runs.save(new ForecastRun(project, caller, capacity, runCount, teamFactorWorseByPercent,
				scopeGrowthP10Percent, scopeGrowthP90Percent, startsOn, workingHoursPerDay, WorkingCalendar.RULE, seed,
				Engine.VERSION, Schedule.PRIORITY_RULE, work.size(), byItem.size(), answer,
				ForecastSnapshots.write(inputs), ForecastSnapshots.write(outputs), Instant.now(this.clock)));
	}

	/**
	 * Every forecast of one plan, newest first.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<ForecastRun> listInProject(UUID callerId, UUID tenantId, UUID projectId) {
		this.projects.get(callerId, tenantId, projectId);
		return this.runs.findAllInProject(tenantId, projectId);
	}

	/**
	 * One forecast, named by identifier.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ForecastNotFoundException if no run in it has that identifier
	 */
	@Transactional(readOnly = true)
	public ForecastRun get(UUID callerId, UUID tenantId, UUID runId) {
		this.memberships.requireMember(callerId, tenantId);
		return this.runs.findInTenant(runId, tenantId).orElseThrow(ForecastNotFoundException::new);
	}

	/**
	 * What a stored run's spread turned out to be made of, ranked.
	 *
	 * <p>
	 * <strong>Nothing was stored for this and nothing is stored by it.</strong> The
	 * durations a ranking needs are a per-item, per-run number — five million of them for
	 * a plan at the scale this product supports — so the run is replayed from its seed
	 * instead, which is what M3a kept a seed for. The payoff is not the space saved: a
	 * stored column would only ever have explained runs made after it existed, and this
	 * explains every forecast this product has ever produced.
	 *
	 * <p>
	 * <strong>And the replay has to prove it is the same engine before it may explain
	 * anything.</strong> A run keeps the six figures it produced, so the replay's own six
	 * are compared against them and any difference refuses the whole thing. That costs
	 * six comparisons after ten thousand simulations and it is the only guard there is: a
	 * ranking produced by a different model is not a rougher ranking of this plan, it is
	 * an exact ranking of a plan nobody forecast, and it would look entirely reasonable.
	 *
	 * <p>
	 * <strong>A source nobody modelled gets no row at all, rather than a row reading
	 * zero.</strong> It never varied, so it measures as nothing either way — but zero
	 * invites a reader to conclude their team has no common cause, when what they did was
	 * decline to model one. Decided from what the run stored, not from what it drew,
	 * which is the rule a run made before there was a calendar already follows.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ForecastNotFoundException if no run in it has that identifier
	 * @throws ForecastReplayMismatchException if replaying it does not reproduce it
	 */
	@Transactional(readOnly = true)
	public List<ContributionResponse> contributionsTo(UUID callerId, UUID tenantId, UUID runId) {
		ForecastRun run = get(callerId, tenantId, runId);
		ForecastInputs inputs = inputsOf(run);
		TeamFactor teamFactor = TeamFactor.from(run.getTeamFactorWorseByPercent().doubleValue());
		ScopeGrowth scopeGrowth = ScopeGrowth.from(run.getScopeGrowthP10Percent().doubleValue(),
				run.getScopeGrowthP90Percent().doubleValue());
		Contributions measured = Contributions.forRun(inputs.items().size());
		Forecast replayed = Engine.run(inputs.toModels(), inputs.toPrecedences(), run.getCapacity(), teamFactor,
				scopeGrowth, run.getSampleCount(), run.getSeed(), measured);
		requireSameRun(run, replayed);

		Map<UUID, WorkItem> named = titlesIn(callerId, tenantId, run.getProject().getId());

		List<ContributionResponse> ranked = new ArrayList<>(inputs.items().size() + 2);
		for (int at = 0; at < inputs.items().size(); at++) {
			UUID itemId = inputs.items().get(at).id();
			WorkItem still = named.get(itemId);
			ranked.add(ContributionResponse.of(itemId, titleOf(still), isArchived(still), measured.ofItem(at)));
		}
		if (!ScopeGrowth.NONE.equals(scopeGrowth)) {
			ranked.add(ContributionResponse.of(ContributionKind.DISCOVERED_WORK, measured.ofDiscoveredWork()));
		}
		if (!TeamFactor.NONE.equals(teamFactor)) {
			ranked.add(ContributionResponse.of(ContributionKind.TEAM_FACTOR, measured.ofTeamFactor()));
		}
		// Ranked here rather than by whoever draws it: the order *is* the feature, and a
		// second caller sorting it a second way would be a second answer to one question.
		ranked.sort(Comparator.comparingDouble(ContributionResponse::shareOfSpread).reversed());
		return List.copyOf(ranked);
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
		ForecastRun run = get(callerId, tenantId, runId);
		if (!run.hasReadableCalendar()) {
			throw new ForecastHasNoCalendarException();
		}
		ForecastInputs inputs = inputsOf(run);
		List<Integer> cutting = positionsOf(candidates, inputs);
		BigDecimal budget = WorkingCalendar.hoursBy(run.getStartsOn(), by, run.getWorkingHoursPerDay());

		List<ItemModel> plan = inputs.toModels();
		ConfidenceBy baseline = new ConfidenceBy(budget.doubleValue());
		// One replay does two jobs: it establishes the baseline every cut is measured
		// against, and it proves this engine still reproduces the run it is about to give
		// advice on.
		requireSameRun(run, replay(run, inputs, plan, baseline));

		Map<UUID, WorkItem> named = titlesIn(callerId, tenantId, run.getProject().getId());
		// Round one of the search and the answer to "what is each worth on its own" are
		// the
		// same simulations, so they are run once and read twice.
		Map<Integer, Double> alone = new HashMap<>();
		List<CutResponse> weighed = new ArrayList<>(cutting.size());
		for (int at : cutting) {
			double reached = confidenceWithout(run, inputs, plan, List.of(at), budget);
			alone.put(at, reached);
			UUID itemId = inputs.items().get(at).id();
			WorkItem still = named.get(itemId);
			weighed.add(new CutResponse(itemId, titleOf(still), isArchived(still), reached, reached - percent(baseline),
					reached >= confidence));
		}
		// Largest first, because the order is the answer — the same reason a contribution
		// ranking is sorted here rather than by whoever draws it.
		weighed.sort(Comparator.comparingDouble(CutResponse::buys).reversed());

		Search search = new Search(run, inputs, plan, budget, confidence, named, cutting, alone, percent(baseline));
		CutPlanResponse together = search.run();
		return new CutOptionsResponse(budget, percent(baseline), percent(baseline) >= confidence, search.simulations(),
				List.copyOf(weighed), together);
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
			return new CutStepResponse(itemId, titleOf(still), isArchived(still), this.reached);
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
		replay(run, inputs, without, counted);
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
			cutting.add(at);
		}
		return cutting;
	}

	/**
	 * The run again, exactly as it was made, with something watching it go past — and
	 * with whatever the caller has imagined away.
	 */
	private static Forecast replay(ForecastRun run, ForecastInputs inputs, List<ItemModel> plan, RunObserver watching) {
		return Engine.run(plan, inputs.toPrecedences(), run.getCapacity(),
				TeamFactor.from(run.getTeamFactorWorseByPercent().doubleValue()), ScopeGrowth
					.from(run.getScopeGrowthP10Percent().doubleValue(), run.getScopeGrowthP90Percent().doubleValue()),
				run.getSampleCount(), run.getSeed(), watching);
	}

	/**
	 * A share of runs as the percentage a person typed, converted once at this boundary —
	 * the model deals in shares and nobody asks for a date at 0.85 confidence.
	 */
	private static double percent(ConfidenceBy counted) {
		return counted.share() * 100.0;
	}

	/**
	 * What the plan calls its work now, live and archived alike.
	 *
	 * <p>
	 * Both listings, because work put away since a run is still work that run was about —
	 * and titles come off the plan rather than the snapshot, which never held one.
	 */
	/**
	 * What a run's work is called now, or nothing where the plan no longer holds it.
	 *
	 * <p>
	 * Stated once because two answers name the same work — a contribution ranking and a
	 * list of cuts — and three-way logic written twice is two chances for one copy to
	 * start rendering a missing item as a blank.
	 */
	private static String titleOf(WorkItem still) {
		return (still != null) ? still.getTitle() : null;
	}

	/** Whether it has been put away since, which is said rather than hidden. */
	private static boolean isArchived(WorkItem still) {
		return still != null && still.getArchivedAt() != null;
	}

	private Map<UUID, WorkItem> titlesIn(UUID callerId, UUID tenantId, UUID projectId) {
		Map<UUID, WorkItem> named = new HashMap<>();
		for (WorkItem live : this.items.list(callerId, tenantId, projectId, false)) {
			named.put(live.getId(), live);
		}
		for (WorkItem away : this.items.list(callerId, tenantId, projectId, true)) {
			named.put(away.getId(), away);
		}
		return named;
	}

	/**
	 * Refuses to explain a run the engine no longer reproduces.
	 *
	 * <p>
	 * Compared on the rounded figures rather than the raw ones, because that is what the
	 * columns hold — and a replay that took the same draws produces the same doubles bit
	 * for bit, so the roundings match exactly or something has genuinely moved. All six,
	 * because a change that moved only the mean or only one tail is exactly the change
	 * nobody would think to look for.
	 */
	private static void requireSameRun(ForecastRun run, Forecast replayed) {
		BigDecimal[] stored = { run.getMeanHours(), run.getP10Hours(), run.getP50Hours(), run.getP80Hours(),
				run.getP90Hours(), run.getP95Hours() };
		double[] again = { replayed.meanHours(), replayed.p10Hours(), replayed.p50Hours(), replayed.p80Hours(),
				replayed.p90Hours(), replayed.p95Hours() };
		for (int at = 0; at < stored.length; at++) {
			if (stored[at].compareTo(ForecastRun.hours(again[at])) != 0) {
				throw new ForecastReplayMismatchException();
			}
		}
	}

	/** What was stored about a run beyond its six headline numbers. */
	public ForecastOutputs outputsOf(ForecastRun run) {
		return ForecastSnapshots.read(run.getOutputs(), ForecastOutputs.class);
	}

	/** What a run was given, which is the whole of what makes it replayable. */
	public ForecastInputs inputsOf(ForecastRun run) {
		return ForecastSnapshots.read(run.getInputs(), ForecastInputs.class);
	}

	private static List<PlannedEstimate> ranges(List<Estimate> estimates) {
		List<PlannedEstimate> ranges = new ArrayList<>(estimates.size());
		for (Estimate estimate : estimates) {
			ranges.add(new PlannedEstimate(estimate.getEstimator().getId(), estimate.getP10Hours(),
					estimate.getP50Hours(), estimate.getP90Hours()));
		}
		return ranges;
	}

	/**
	 * What this forecast did not do.
	 *
	 * <p>
	 * <strong>Two of these used to be on every run and are on none of them now.</strong>
	 * {@code no_team_factor} and {@code no_scope_uncertainty} described the engine rather
	 * than the plan — it sampled every item independently and forecast only the work
	 * somebody had written down — and M3b is the milestone that removed their cause
	 * rather than their wording. What is left describes the plan being forecast, which is
	 * why those two could be retired and these cannot.
	 *
	 * <p>
	 * A run made before that is still carrying them, which is the whole reason
	 * limitations are stored rather than worked out at read time. They are gone from what
	 * this writes and not from what it can read.
	 */
	private static List<ForecastLimitation> limitations(List<PlannedItem> planned, boolean droppedArrows) {
		Set<ForecastLimitation> found = EnumSet.noneOf(ForecastLimitation.class);
		if (droppedArrows) {
			found.add(ForecastLimitation.DEPENDENCIES_ON_ARCHIVED_WORK);
		}
		for (PlannedItem item : planned) {
			if (item.estimates().isEmpty()) {
				found.add(ForecastLimitation.UNESTIMATED_ITEMS);
			}
			for (PlannedEstimate range : item.estimates()) {
				if (arguesWithItself(range)) {
					found.add(ForecastLimitation.INCONSISTENT_ESTIMATES);
				}
			}
		}
		return List.copyOf(found);
	}

	/**
	 * Whether somebody's middle number sits a long way from the one their own two ends
	 * imply — which says the three were not thought about together, and nothing else. The
	 * estimate is used exactly as given either way.
	 *
	 * <p>
	 * Asked of {@link EstimateQuality} rather than worked out here, so that a forecast
	 * and the plan screen cannot come to different conclusions about one estimate. The
	 * threshold that used to be a constant in this class is now beside the arithmetic it
	 * bounds.
	 */
	private static boolean arguesWithItself(PlannedEstimate range) {
		return EstimateQuality
			.of(range.p10Hours().doubleValue(), range.p50Hours().doubleValue(), range.p90Hours().doubleValue())
			.inconsistent();
	}

}
