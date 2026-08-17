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

	ForecastController(ForecastService forecasts) {
		this.forecasts = forecasts;
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

	private ForecastResponse described(ForecastRun run) {
		return ForecastResponse.of(run, this.forecasts.outputsOf(run));
	}

}
