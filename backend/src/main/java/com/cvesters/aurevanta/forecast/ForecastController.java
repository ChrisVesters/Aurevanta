package com.cvesters.aurevanta.forecast;

import java.util.List;
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

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * Asking for a forecast, and reading the ones already given.
 *
 * <p>
 * <strong>Three endpoints and no fourth.</strong> Nothing updates a run and nothing
 * deletes one: it is the record of what this product said on a date, and M10 asks whether
 * that date has been sliding — a question only an unedited history can answer.
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

	ForecastController(ForecastService forecasts, MovementService movements) {
		this.forecasts = forecasts;
		this.movements = movements;
	}

	@PostMapping("/api/projects/{projectId}/forecasts")
	@ResponseStatus(HttpStatus.CREATED)
	ForecastResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
			@Valid @RequestBody CreateForecastRequest request) {
		return described(this.forecasts.run(caller.userId(), caller.tenantId(), projectId, request.capacity(),
				request.sampleCount(), request.teamFactorWorseByPercent(), request.scopeGrowthP10Percent(),
				request.scopeGrowthP90Percent(), request.startsOn(), request.workingHoursPerDay()));
	}

	/**
	 * Every forecast of one plan, newest first — which is what makes a second one worth
	 * asking for, since a forecast on its own says less than a forecast beside the one
	 * before it.
	 */
	@GetMapping("/api/projects/{projectId}/forecasts")
	List<ForecastResponse> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		return this.forecasts.listInProject(caller.userId(), caller.tenantId(), projectId)
			.stream()
			.map(this::described)
			.toList();
	}

	@GetMapping("/api/forecasts/{runId}")
	ForecastResponse read(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID runId) {
		return described(this.forecasts.get(caller.userId(), caller.tenantId(), runId));
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
	 * costs what it costs today — the two-second budget {@code m3a-plan.md} measured is
	 * what keeps one inside the request that asked for it, and most callers never open
	 * this.
	 */
	@GetMapping("/api/forecasts/{runId}/contributions")
	List<ContributionResponse> contributions(@AuthenticationPrincipal AuthenticatedUser caller,
			@PathVariable UUID runId) {
		return this.forecasts.contributionsTo(caller.userId(), caller.tenantId(), runId);
	}

	/**
	 * Why the date moved between this run and another of the same plan.
	 *
	 * <p>
	 * <strong>The terms add up to the whole distance between the two</strong>, which is
	 * the only reason a sentence like "out eight days: five new scope, four re-estimates,
	 * one of progress" is worth publishing at all. It costs six simulations and says so.
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
		return this.forecasts.cutsFor(caller.userId(), caller.tenantId(), runId, request.by(), request.confidence(),
				request.candidates());
	}

	private ForecastResponse described(ForecastRun run) {
		return ForecastResponse.of(run, this.forecasts.outputsOf(run));
	}

}
