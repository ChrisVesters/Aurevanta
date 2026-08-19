package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.EstimateQuality;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ForecastTerms;
import com.cvesters.aurevanta.forecast.model.Schedule;
import com.cvesters.aurevanta.forecast.model.ScopeGrowth;
import com.cvesters.aurevanta.forecast.model.TeamFactor;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.CapacityNotApplicableException;
import com.cvesters.aurevanta.problem.CapacityRequiredException;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NothingToForecastException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.ScopeGrowthOutOfOrderException;
import com.cvesters.aurevanta.problem.WorkNeedsMoreThanTheTeamHasException;
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
 *
 * <p>
 * <strong>This makes forecasts and reads them, and nothing else.</strong> Everything that
 * <em>explains</em> one lives beside it and writes nothing — {@link ContributionService}
 * ranks what widened the band, {@link CutService} weighs what to drop,
 * {@link HireService} weighs another unit, {@link MovementService} accounts for why the
 * date moved and {@link ThroughputService} answers the same question from the plan's own
 * history. All five replay through {@link ForecastReplays}. They were four more methods
 * on this class until they were five features, at which point the class was the only
 * thing in the package that knew about all of them.
 */
@Service
public class ForecastService {

	/**
	 * The three confidences the calendar's control offers, and the only three anything
	 * publishes a second reading at.
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

		Map<UUID, Integer> declared = new HashMap<>();
		List<PlannedPool> team = new ArrayList<>(pools.size());
		int units = 0;
		for (Resource pool : pools) {
			declared.put(pool.getId(), pool.getUnits());
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
			Integer holds = declared.get(requirement.getResource().getId());
			if (holds == null) {
				droppedNeeds = true;
				continue;
			}
			// And a pool still in use that has since been shrunk below what this work
			// needs is refused rather than left out: dropping it would make the work
			// generic and the forecast sooner than the plan can possibly be delivered.
			// `requirement` refuses this on the way in; a pool can be shrunk afterwards,
			// and `resource` may not look at what depends on it without pointing an arrow
			// back the way it came.
			if (requirement.getUnits() > holds) {
				throw new WorkNeedsMoreThanTheTeamHasException();
			}
			needs.add(new PlannedNeed(item, requirement.getResource().getId(), requirement.getUnits()));
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
		// assumed, and the drift detector, which walks a whole history — and
		// the snapshot
		// beside
		// it holds five hundred items and every range anybody typed.
		ForecastTerms terms = new ForecastTerms(Engine.VERSION, Schedule.PRIORITY_RULE, WorkingCalendar.RULE,
				workingHoursPerDay, concurrency, ForecastSnapshots.write(team), teamFactorWorseByPercent,
				scopeGrowthP10Percent, scopeGrowthP90Percent, startsOn);
		return this.runs.save(new ForecastRun(project, caller, terms, runCount, seed, work.size(), byItem.size(),
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
	 * somebody had written down — and the common-cause model is the change that removed
	 * their cause rather than their wording. What is left describes the plan being
	 * forecast, which is why those two could be retired and these cannot.
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
