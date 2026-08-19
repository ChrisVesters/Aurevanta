package com.cvesters.aurevanta.forecast;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.resource.Resource;
import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * Asking for a forecast, and reading the ones already given.
 *
 * <p>
 * <strong>Three endpoints and no fourth.</strong> Nothing updates a run and nothing
 * deletes one: it is the record of what this product said on a date, and the reporting
 * work asks whether that date has been sliding — a question only an unedited history can
 * answer.
 *
 * <p>
 * Addressed the way an item is: made and listed <em>within</em> a plan, which is the only
 * moment the plan has to be named, and read on its own once it exists.
 *
 * <p>
 * Every one of them is reachable by every member, and the organisation comes from the
 * caller's token in all three.
 */
@RestController
class ForecastController {

	private final ForecastService forecasts;

	private final MovementService movements;

	private final ContributionService contributions;

	private final CutService cuts;

	private final HireService hires;

	ForecastController(ForecastService forecasts, MovementService movements, ContributionService contributions,
			CutService cuts, HireService hires) {
		this.forecasts = forecasts;
		this.movements = movements;
		this.contributions = contributions;
		this.cuts = cuts;
		this.hires = hires;
	}

	@PostMapping("/api/projects/{projectId}/forecasts")
	@ResponseStatus(HttpStatus.CREATED)
	ForecastResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
			@Valid @RequestBody CreateForecastRequest request) {
		return described(
				this.forecasts.run(caller.userId(), caller.tenantId(), projectId, request.capacity(),
						request.sampleCount(), request.teamFactorWorseByPercent(), request.scopeGrowthP10Percent(),
						request.scopeGrowthP90Percent(), request.startsOn(), request.workingHoursPerDay()),
				this.forecasts.poolsOf(caller.userId(), caller.tenantId()));
	}

	/**
	 * Every forecast of one plan, newest first — which is what makes a second one worth
	 * asking for, since a forecast on its own says less than a forecast beside the one
	 * before it.
	 *
	 * <p>
	 * <strong>And whether the date keeps moving out</strong>, answered here rather than
	 * by a request of its own: it is a property of the sequence and not of any run in it,
	 * and it costs no simulation — every date it reads is one this answer already
	 * carries.
	 */
	@GetMapping("/api/projects/{projectId}/forecasts")
	ForecastHistoryResponse list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		List<ForecastRun> found = this.forecasts.listInProject(caller.userId(), caller.tenantId(), projectId);
		Map<UUID, Resource> pools = this.forecasts.poolsOf(caller.userId(), caller.tenantId());
		return new ForecastHistoryResponse(found.stream().map((run) -> described(run, pools)).toList(),
				DriftResponse.over(found));
	}

	@GetMapping("/api/forecasts/{runId}")
	ForecastResponse read(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID runId) {
		return described(this.forecasts.get(caller.userId(), caller.tenantId(), runId),
				this.forecasts.poolsOf(caller.userId(), caller.tenantId()));
	}

	/**
	 * What this run's spread turned out to be made of, largest first.
	 *
	 * <p>
	 * <strong>A read that stores nothing and costs a whole forecast.</strong> The ranking
	 * is worked out by replaying the run from its seed rather than from anything kept
	 * beside it, which is why it can answer for every forecast this product has ever made
	 * rather than only for the ones made since somebody added a column.
	 *
	 * <p>
	 * Its own request rather than part of the forecast, so that asking for a forecast
	 * costs what it costs today — the two-second budget
	 * {@code docs/design/simulation-engine.md} measured is what keeps one inside the
	 * request that asked for it, and most callers never open this.
	 */
	@GetMapping("/api/forecasts/{runId}/contributions")
	List<ContributionResponse> contributions(@AuthenticationPrincipal AuthenticatedUser caller,
			@PathVariable UUID runId) {
		return this.contributions.forRun(caller.userId(), caller.tenantId(), runId);
	}

	/**
	 * Why the date moved between this run and another of the same plan.
	 *
	 * <p>
	 * <strong>The terms add up to the whole distance between the two</strong>, which is
	 * the only reason a sentence like "out eight days: five new scope, four re-estimates,
	 * one of progress" is worth publishing at all. It costs seven simulations and says
	 * so.
	 *
	 * <p>
	 * Which of the two runs is the older one is worked out here rather than demanded —
	 * that is a fact about the rows, and a refusal for naming them the wrong way round
	 * would be one nobody could act on without going and looking it up.
	 */
	@GetMapping("/api/forecasts/{runId}/movement")
	MovementResponse movement(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID runId,
			@RequestParam UUID since) {
		return this.movements.between(caller.userId(), caller.tenantId(), since, runId);
	}

	/**
	 * What it would take to hit a date, weighed against this run.
	 *
	 * <p>
	 * <strong>A POST that writes nothing</strong>, and one because the body carries a
	 * list of identifiers — a dozen of them do not belong in a URL. The same shape
	 * {@code /api/estimates/quality} already takes, and for the same reason.
	 *
	 * <p>
	 * It proposes and decides nothing. Acting on the answer means archiving work on the
	 * plan screen, where somebody can see what else it is connected to.
	 */
	@PostMapping("/api/forecasts/{runId}/cuts")
	CutOptionsResponse cuts(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID runId,
			@Valid @RequestBody CutsRequest request) {
		return this.cuts.cutsFor(caller.userId(), caller.tenantId(), runId, request.by(), request.confidence(),
				request.candidates());
	}

	/**
	 * What hiring into one pool would be worth, weighed against this run.
	 *
	 * <p>
	 * <strong>A POST that writes nothing</strong>, like the cuts beside it, and one for
	 * the same reason: it is a question rather than a change. Nothing here hires anybody,
	 * and the answer is a number of days rather than a recommendation — what a person
	 * costs and how long they take to be useful are facts this server does not have.
	 */
	@PostMapping("/api/forecasts/{runId}/hires")
	HireOptionsResponse hires(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID runId,
			@Valid @RequestBody HiresRequest request) {
		return this.hires.hiresFor(caller.userId(), caller.tenantId(), runId, request.resourceId(), request.units());
	}

	/**
	 * One run as the API describes it, with the team it was scheduled against named.
	 *
	 * <p>
	 * The organisation's pools are looked up once per request rather than once per run: a
	 * plan's whole history is described in one answer, and a name is the only thing here
	 * that comes from anywhere but the run itself.
	 */
	private ForecastResponse described(ForecastRun run, Map<UUID, Resource> pools) {
		return ForecastResponse.of(run, this.forecasts.outputsOf(run), pools);
	}

}
