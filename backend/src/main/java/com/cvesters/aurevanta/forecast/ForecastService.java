package com.cvesters.aurevanta.forecast;

import java.time.Clock;
import java.time.Instant;
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
import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.LogNormalFit;
import com.cvesters.aurevanta.forecast.model.Schedule;
import com.cvesters.aurevanta.forecast.model.TeamFactor;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NothingToForecastException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
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

	/**
	 * How far a stated middle may sit from the one its own two ends imply before the run
	 * mentions it. A quarter either way — far enough that 3/5/8 and its neighbours pass
	 * unremarked, close enough to catch somebody who put 10 in the middle of 5 and 40.
	 *
	 * <p>
	 * It changes nothing about the forecast. The fit uses the ends whatever the middle
	 * says; this only decides whether the answer arrives with a note attached.
	 */
	private static final double CONSISTENT_ENOUGH = 0.25;

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
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 * @throws NothingToForecastException if no work in the plan carries an estimate
	 */
	@Transactional
	public ForecastRun run(UUID callerId, UUID tenantId, UUID projectId, int capacity, Integer sampleCount) {
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
		// No common cause yet, which is what NO_TEAM_FACTOR below reports. The
		// engine can model one; nobody has been asked for the number, and picking
		// one here would be a claim about a team this server has never met. M3b
		// step 4 is where it gets asked for.
		Forecast answer = Engine.run(inputs.toModels(), inputs.toPrecedences(), capacity, TeamFactor.NONE, runCount,
				seed);
		ForecastOutputs outputs = new ForecastOutputs(answer.histogram(),
				limitations(planned, kept.size() < arrows.size()));
		return this.runs.save(new ForecastRun(project, caller, capacity, runCount, seed, Engine.VERSION,
				Schedule.PRIORITY_RULE, work.size(), byItem.size(), answer, ForecastSnapshots.write(inputs),
				ForecastSnapshots.write(outputs), Instant.now(this.clock)));
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
	 * The first two are unconditional and describe the engine rather than the plan: M3a
	 * samples every item independently and forecasts only the work somebody wrote down,
	 * and both of those make the band narrower than the truth. They are stored rather
	 * than worked out at read time so that a run made today still says it lacked them
	 * after M3b has built what they name.
	 */
	private static List<ForecastLimitation> limitations(List<PlannedItem> planned, boolean droppedArrows) {
		Set<ForecastLimitation> found = EnumSet.of(ForecastLimitation.NO_TEAM_FACTOR,
				ForecastLimitation.NO_SCOPE_UNCERTAINTY);
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
	 */
	private static boolean arguesWithItself(PlannedEstimate range) {
		LogNormalFit fit = LogNormalFit.from(range.p10Hours().doubleValue(), range.p90Hours().doubleValue());
		double agreement = fit.consistency(range.p50Hours().doubleValue());
		return Math.abs(agreement - 1.0) > CONSISTENT_ENOUGH;
	}

}
