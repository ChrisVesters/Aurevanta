package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedNeed;
import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedPool;
import com.cvesters.aurevanta.forecast.model.ConfidenceBy;
import com.cvesters.aurevanta.forecast.model.Contributions;
import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.EstimateQuality;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ForecastTerms;
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
import com.cvesters.aurevanta.problem.CapacityNotApplicableException;
import com.cvesters.aurevanta.problem.CapacityRequiredException;
import com.cvesters.aurevanta.problem.ForecastHasNoCalendarException;
import com.cvesters.aurevanta.problem.ForecastHasNoResourcesException;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NothingToForecastException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.ResourceNotInForecastException;
import com.cvesters.aurevanta.problem.ScopeGrowthOutOfOrderException;
import com.cvesters.aurevanta.problem.TooManyCandidatesException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.requirement.Requirement;
import com.cvesters.aurevanta.requirement.RequirementService;
import com.cvesters.aurevanta.resource.Resource;
import com.cvesters.aurevanta.resource.ResourceService;
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

	/**
	 * The three confidences M4's control offers, and the only three anything publishes a
	 * second reading at.
	 *
	 * <p>
	 * The other two percentiles have no control and no need. Stated here because two
	 * things ask — an account of a movement, and a drift over a history — and a screen
	 * offering a confidence the server has no answer for is the failure this prevents.
	 */
	static final int[] CONFIDENCES = { 50, 80, 95 };

	private final ForecastRunRepository runs;

	private final ProjectService projects;

	private final WorkItemService items;

	private final EstimateService estimates;

	private final DependencyService dependencies;

	private final ResourceService resources;

	private final RequirementService requirements;

	private final MembershipService memberships;

	private final Clock clock;

	ForecastService(ForecastRunRepository runs, ProjectService projects, WorkItemService items,
			EstimateService estimates, DependencyService dependencies, ResourceService resources,
			RequirementService requirements, MembershipService memberships, Clock clock) {
		this.runs = runs;
		this.projects = projects;
		this.items = items;
		this.estimates = estimates;
		this.dependencies = dependencies;
		this.resources = resources;
		this.requirements = requirements;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Forecasts a plan and writes down what was asked, what was assumed and what came
	 * back.
	 * @param capacity how many items may be under way at once, or null when the
	 * organisation has described its team and the pools say. Exactly one of the two, and
	 * no default exists anywhere: it moves the answer by more than half, and a server
	 * that picked would be making a claim about a team it has never met.
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
	public ForecastRun run(UUID callerId, UUID tenantId, UUID projectId, Integer capacity, Integer sampleCount,
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
		// The team as it stands, in the order it was declared — which is the order work
		// that names nothing takes a unit in, so it is part of the model rather than of a
		// listing. Put-away pools are left out for the reason put-away work is: a
		// resource
		// the organisation no longer has cannot be scheduled against.
		List<Resource> pools = this.resources.list(callerId, tenantId, false);
		List<Requirement> needed = this.requirements.listInProject(callerId, tenantId, projectId);

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

		Set<UUID> declared = new HashSet<>();
		List<PlannedPool> team = new ArrayList<>(pools.size());
		int units = 0;
		for (Resource pool : pools) {
			declared.add(pool.getId());
			team.add(new PlannedPool(pool.getId(), pool.getUnits()));
			units += pool.getUnits();
		}
		List<PlannedNeed> needs = new ArrayList<>(needed.size());
		boolean droppedNeeds = false;
		for (Requirement requirement : needed) {
			UUID item = requirement.getWorkItem().getId();
			if (!forecast.contains(item)) {
				continue;
			}
			// A pool that has been put away is left out and said out loud, exactly as an
			// arrow into archived work is.
			if (declared.contains(requirement.getResource().getId())) {
				needs.add(new PlannedNeed(item, requirement.getResource().getId(), requirement.getUnits()));
			}
			else {
				droppedNeeds = true;
			}
		}
		// **One of the two, never both and never neither.** Once a team is described the
		// concurrency is what it holds, and a second number beside it would leave a
		// reader
		// unable to say which one bound the answer; with no team described there is
		// nothing
		// else that could bound it. Refused rather than ignored, which is the rule
		// `progress_not_applicable` states: silently dropping input is worse than
		// refusing
		// it, because the person is not told they have been overruled.
		if (team.isEmpty() && capacity == null) {
			throw new CapacityRequiredException();
		}
		if (!team.isEmpty() && capacity != null) {
			throw new CapacityNotApplicableException();
		}
		int concurrency = team.isEmpty() ? capacity : units;

		ForecastInputs inputs = new ForecastInputs(planned, kept, team, needs);
		int runCount = (sampleCount != null) ? sampleCount : Engine.DEFAULT_SAMPLE_COUNT;
		long seed = ThreadLocalRandom.current().nextLong();
		// Percentages a person typed, turned into the two distributions the engine
		// models with. Both refuse what they cannot fit, and neither refusal is
		// reachable from here: the validation above rules out a negative, and the
		// check at the top rules out a range that descends.
		TeamFactor teamFactor = TeamFactor.from(teamFactorWorseByPercent.doubleValue());
		ScopeGrowth scopeGrowth = ScopeGrowth.from(scopeGrowthP10Percent.doubleValue(),
				scopeGrowthP90Percent.doubleValue());
		Forecast answer = Engine.run(inputs.toModels(), inputs.toPrecedences(), inputs.toResourcing(concurrency),
				teamFactor, scopeGrowth, runCount, seed);
		ForecastOutputs outputs = new ForecastOutputs(answer.histogram(),
				limitations(planned, kept.size() < arrows.size(), inputs, droppedNeeds));
		// The calendar is written down and not used: nothing about a working day reaches
		// the engine, which is what keeps a calendar change from being a model change.
		// The
		// rule's name goes with it so the run resolves under the calendar it was made
		// with
		// rather than the one this code has by the time somebody reads it.
		// The team goes on the row as well as into the snapshot, and the duplication is
		// deliberate: two readers want it per run — the panel that prints what a forecast
		// assumed, and M10's detector, which walks a whole history — and the snapshot
		// beside
		// it holds five hundred items and every range anybody typed.
		return this.runs.save(new ForecastRun(project, caller, concurrency, runCount, teamFactorWorseByPercent,
				scopeGrowthP10Percent, scopeGrowthP90Percent, startsOn, workingHoursPerDay, WorkingCalendar.RULE, seed,
				Engine.VERSION, Schedule.PRIORITY_RULE, ForecastSnapshots.write(team), work.size(), byItem.size(),
				answer, ForecastSnapshots.write(inputs), ForecastSnapshots.write(outputs), Instant.now(this.clock)));
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
		Contributions measured = Contributions.forRun(inputs.items().size());
		// The same replay an inverse query makes, through the same method: two ways of
		// re-running one stored forecast would eventually be one right way and one that
		// had
		// stopped being told about a parameter.
		requireSameRun(run, replay(run, inputs, inputs.toModels(), measured));

		Map<UUID, WorkItem> named = titlesIn(callerId, tenantId, run.getProject().getId());

		List<ContributionResponse> ranked = new ArrayList<>(inputs.items().size() + 2);
		for (int at = 0; at < inputs.items().size(); at++) {
			UUID itemId = inputs.items().get(at).id();
			WorkItem still = named.get(itemId);
			ranked.add(ContributionResponse.of(itemId, titleOf(still), isArchived(still), measured.ofItem(at)));
		}
		if (!ScopeGrowth.NONE.equals(scopeGrowthOf(run))) {
			ranked.add(ContributionResponse.of(ContributionKind.DISCOVERED_WORK, measured.ofDiscoveredWork()));
		}
		if (!TeamFactor.NONE.equals(teamFactorOf(run))) {
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

		double stands = percent(baseline);

		Map<UUID, WorkItem> named = titlesIn(callerId, tenantId, run.getProject().getId());
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
			weighed.add(new CutResponse(itemId, titleOf(still), isArchived(still), reached, reached - stands,
					reached >= confidence));
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
	 * What adding to one pool would be worth, measured against a stored run.
	 *
	 * <p>
	 * <strong>M7's machinery answering `roadmap.md`'s most compelling question.</strong>
	 * Every counterfactual is a replay of the stored run from its own seed with one pool
	 * larger — nothing is written, and forty simulations could go past without
	 * {@code forecast_runs} gaining a row.
	 *
	 * <p>
	 * <strong>The pairing is exact for free here, where a cut had to work for
	 * it.</strong> M7's cut takes a draw and discards it, because an item that took no
	 * draw would shift every later number in the run and turn the measurement into noise.
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
		ForecastRun run = get(callerId, tenantId, runId);
		if (!run.hasReadableCalendar()) {
			throw new ForecastHasNoCalendarException();
		}
		// Looked up so that a pool belonging to somebody else is not there at all, rather
		// than being reported as one this run did not use.
		this.resources.get(callerId, tenantId, resourceId);
		ForecastInputs inputs = inputsOf(run);
		if (inputs.pools().isEmpty()) {
			throw new ForecastHasNoResourcesException();
		}
		if (inputs.pools().stream().noneMatch((pool) -> pool.resourceId().equals(resourceId))) {
			throw new ResourceNotInForecastException();
		}

		List<ItemModel> plan = inputs.toModels();
		// The baseline does two jobs, as M7's does: it is what every row below is
		// measured
		// against, and it proves this engine still reproduces the run it is about to give
		// advice on.
		Forecast stands = replay(run, inputs, plan, RunObserver.NONE);
		requireSameRun(run, stands);

		List<List<HireStepResponse>> steps = new ArrayList<>(CONFIDENCES.length);
		for (int at = 0; at < CONFIDENCES.length; at++) {
			steps.add(new ArrayList<>(units));
		}
		for (int extra = 1; extra <= units; extra++) {
			// Cumulative and measured rather than the first row multiplied: the second
			// person is worth less than the first, and how much less is the answer.
			ForecastInputs larger = inputs.withMore(resourceId, extra);
			Forecast with = replay(run, larger, plan, RunObserver.NONE);
			for (int at = 0; at < CONFIDENCES.length; at++) {
				steps.get(at).add(step(run, extra, stands, with, CONFIDENCES[at]));
			}
		}

		List<HireAtResponse> at = new ArrayList<>(CONFIDENCES.length);
		for (int which = 0; which < CONFIDENCES.length; which++) {
			at.add(new HireAtResponse(CONFIDENCES[which], dateOf(run, stands, CONFIDENCES[which]),
					List.copyOf(steps.get(which))));
		}
		return new HireOptionsResponse(resourceId, units + 1, at);
	}

	/**
	 * One row: the date that many extra units buys, and how much sooner it is.
	 *
	 * <p>
	 * Days are the difference between two <em>dates</em> and never hours converted, which
	 * is M4's step function met from the same direction M10's decomposition met it: each
	 * end is rounded up to a whole day on its own.
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
	 * The run again, exactly as it was made, with something watching it go past — and
	 * with whatever the caller has imagined away.
	 */
	private static Forecast replay(ForecastRun run, ForecastInputs inputs, List<ItemModel> plan, RunObserver watching) {
		return replayWith(plan, inputs, termsOf(run), run.getSampleCount(), run.getSeed(), watching);
	}

	/**
	 * The engine run for a plan and a set of terms that need not be any one run's.
	 *
	 * <p>
	 * <strong>The one place a stored forecast is re-run.</strong> A decomposition varies
	 * the terms deliberately — the older plan under the newer capacity, and so on — so it
	 * cannot take them off a row the way the replay above does. Two ways of re-running
	 * one stored forecast would eventually be one right way and one that had drifted, and
	 * the drifted one would explain a plan nobody forecast while looking entirely
	 * reasonable.
	 */
	static Forecast replayWith(List<ItemModel> plan, ForecastInputs inputs, ForecastTerms terms, int sampleCount,
			long seed, RunObserver watching) {
		return Engine.run(plan, inputs.toPrecedences(), inputs.toResourcing(terms.capacity()),
				TeamFactor.from(terms.teamFactorWorseByPercent().doubleValue()), ScopeGrowth
					.from(terms.scopeGrowthP10Percent().doubleValue(), terms.scopeGrowthP90Percent().doubleValue()),
				sampleCount, seed, watching);
	}

	/** What a run was asked on, as {@code Comparison} and a decomposition take it. */
	static ForecastTerms termsOf(ForecastRun run) {
		return new ForecastTerms(run.getEngineVersion(), run.getPriorityRule(), run.getCalendarRule(),
				run.getWorkingHoursPerDay(), run.getCapacity(), run.getResourcing(), run.getTeamFactorWorseByPercent(),
				run.getScopeGrowthP10Percent(), run.getScopeGrowthP90Percent(), run.getStartsOn());
	}

	/**
	 * The pools a stored run was scheduled against, as it wrote them down.
	 *
	 * <p>
	 * Read from the column rather than from the snapshot beside it, which holds the same
	 * list inside a document of five hundred items — the panel wants this for every run
	 * of a plan at once.
	 */
	static List<PlannedPool> teamOf(ForecastRun run) {
		if (run.getResourcing() == null) {
			return List.of();
		}
		return List.of(ForecastSnapshots.read(run.getResourcing(), PlannedPool[].class));
	}

	/** Whether a replay of this run still produces the run — M6's guard, shared. */
	static void requireReproduces(ForecastRun run, Forecast replayed) {
		requireSameRun(run, replayed);
	}

	/**
	 * The two M3b assumptions a run stored, as the engine takes them.
	 *
	 * <p>
	 * Read off the row in one place because two readings would eventually be one reading
	 * and one stale one — and the stale one would replay a plan nobody forecast while
	 * looking entirely reasonable. A contribution ranking needs them a second time for
	 * something else: whether either was modelled at all decides whether it gets a row,
	 * and a source nobody modelled must get none rather than one reading zero.
	 */
	private static TeamFactor teamFactorOf(ForecastRun run) {
		return TeamFactor.from(run.getTeamFactorWorseByPercent().doubleValue());
	}

	private static ScopeGrowth scopeGrowthOf(ForecastRun run) {
		return ScopeGrowth.from(run.getScopeGrowthP10Percent().doubleValue(),
				run.getScopeGrowthP90Percent().doubleValue());
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
	 * The team a run was scheduled against, with today's names on it.
	 *
	 * <p>
	 * <strong>The units come off the run and the names off the organisation</strong>,
	 * which is the split a contribution ranking already makes for the work it names: what
	 * a pool was called is not a thing that moved, and what it held then is.
	 *
	 * <p>
	 * One lookup of the organisation's pools serves however many runs are being
	 * described, which is what makes a listing of a plan's whole history one query rather
	 * than one per row. Archived pools are in it, because a run scheduled against one has
	 * to be able to say what it was called.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public Map<UUID, Resource> poolsOf(UUID callerId, UUID tenantId) {
		Map<UUID, Resource> byId = new HashMap<>();
		for (boolean archived : new boolean[] { false, true }) {
			for (Resource pool : this.resources.list(callerId, tenantId, archived)) {
				byId.put(pool.getId(), pool);
			}
		}
		return byId;
	}

	/** One run's team, named — the shape {@code ForecastResponse} publishes. */
	static List<ForecastResourceResponse> teamOf(ForecastRun run, Map<UUID, Resource> pools) {
		List<PlannedPool> declared = teamOf(run);
		List<ForecastResourceResponse> described = new ArrayList<>(declared.size());
		for (PlannedPool pool : declared) {
			described.add(ForecastResourceResponse.of(pool.resourceId(), pool.units(), pools.get(pool.resourceId())));
		}
		return described;
	}

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
	private static List<ForecastLimitation> limitations(List<PlannedItem> planned, boolean droppedArrows,
			ForecastInputs inputs, boolean droppedNeeds) {
		Set<ForecastLimitation> found = EnumSet.noneOf(ForecastLimitation.class);
		if (droppedArrows) {
			found.add(ForecastLimitation.DEPENDENCIES_ON_ARCHIVED_WORK);
		}
		if (droppedNeeds) {
			found.add(ForecastLimitation.REQUIREMENTS_ON_ARCHIVED_RESOURCES);
		}
		// Only where it can change the answer. With one pool — and with none, which is a
		// capacity — naming nothing and naming that pool are the same claim, so a warning
		// would fire on every forecast anybody ran and mean nothing on any of them.
		if (inputs.pools().size() > 1 && namesNothing(inputs)) {
			found.add(ForecastLimitation.UNASSIGNED_WORK);
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

	/** Whether any of the work in this plan named no resource at all. */
	private static boolean namesNothing(ForecastInputs inputs) {
		Set<UUID> named = new HashSet<>();
		for (PlannedNeed need : inputs.needs()) {
			named.add(need.workItemId());
		}
		return named.size() < inputs.items().size();
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
