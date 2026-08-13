package com.cvesters.aurevanta.dependency;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * How a plan is joined up.
 *
 * <p>
 * <strong>Three endpoints at three paths, and none of them repeats an identifier another
 * one settles.</strong> Drawing an edge names both its ends in the body, because they are
 * of equal standing and neither is the thing the other hangs off; listing is by plan,
 * because a screen showing a plan needs every edge in it at once and asking per item
 * would be five hundred requests to draw one page; and rubbing one out names the edge
 * alone, since it already knows which plan it is in.
 *
 * <p>
 * <strong>The one real {@code DELETE} in this application.</strong> Everything else
 * archives — a project, an item — because the row is history somebody will read later. An
 * edge is not history: it is a constraint the scheduler obeys until it is gone, and one
 * drawn by mistake that merely went dormant would be a plan quietly forecasting around a
 * line nobody could see.
 *
 * <p>
 * Every one of them is reachable by every member, and the organisation comes from the
 * caller's token in all three.
 */
@RestController
class DependencyController {

	private final DependencyService dependencies;

	DependencyController(DependencyService dependencies) {
		this.dependencies = dependencies;
	}

	@PostMapping("/api/dependencies")
	@ResponseStatus(HttpStatus.CREATED)
	DependencyResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
			@Valid @RequestBody CreateDependencyRequest request) {
		return DependencyResponse.of(this.dependencies.create(caller.userId(), caller.tenantId(),
				request.predecessorItemId(), request.successorItemId(), request.lagHours()));
	}

	@GetMapping("/api/projects/{projectId}/dependencies")
	List<DependencyResponse> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		return this.dependencies.listInProject(caller.userId(), caller.tenantId(), projectId)
			.stream()
			.map(DependencyResponse::of)
			.toList();
	}

	@DeleteMapping("/api/dependencies/{dependencyId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID dependencyId) {
		this.dependencies.delete(caller.userId(), caller.tenantId(), dependencyId);
	}

}
