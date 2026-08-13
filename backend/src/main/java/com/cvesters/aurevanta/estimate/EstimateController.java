package com.cvesters.aurevanta.estimate;

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
 * Three-point estimates: recording one, and reading back what a plan currently carries.
 *
 * <p>
 * <strong>Two endpoints, and there is deliberately no third.</strong> Nothing here
 * updates or deletes an estimate — a revision is a new {@code POST}, and both rows
 * survive. The absence is the feature: M8 measures how often somebody's band contained
 * the truth, and an endpoint that could rewrite history would make that measurement a
 * question about what people think now.
 *
 * <p>
 * The estimator is always the caller. There is no field for it in the request and no way
 * to record a range on somebody else's behalf, because M8 reports per estimator and an
 * estimate attributed to the wrong person is worse than no estimate at all.
 */
@RestController
class EstimateController {

	private final EstimateService estimates;

	EstimateController(EstimateService estimates) {
		this.estimates = estimates;
	}

	@PostMapping("/api/items/{itemId}/estimates")
	@ResponseStatus(HttpStatus.CREATED)
	EstimateResponse record(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId,
			@Valid @RequestBody RecordEstimateRequest request) {
		return EstimateResponse.of(this.estimates.record(caller.userId(), caller.tenantId(), itemId, request.p10Hours(),
				request.p50Hours(), request.p90Hours()));
	}

	/**
	 * What everybody currently thinks about the work in one plan.
	 *
	 * <p>
	 * A plan's worth in one request, because the screen that shows a plan needs all of it
	 * — and because asking per item would be five hundred requests to draw one page. What
	 * comes back is enough to say which items are covered and what each person last said,
	 * which is the whole of what M2 promised to make visible.
	 */
	@GetMapping("/api/projects/{projectId}/estimates")
	List<EstimateResponse> current(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		return this.estimates.currentInProject(caller.userId(), caller.tenantId(), projectId)
			.stream()
			.map(EstimateResponse::of)
			.toList();
	}

}
