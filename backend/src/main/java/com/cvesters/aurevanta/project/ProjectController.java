package com.cvesters.aurevanta.project;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * The plans one organisation holds.
 *
 * <p>
 * Every endpoint here is reachable by every member: roles govern administration, and
 * estimation is a team activity. The organisation is never named in a path or a body — it
 * comes from the caller's access token, as it does everywhere else that touches
 * tenant-owned data, so there is nothing here to point at somebody else's plans.
 *
 * <p>
 * No handle in any of these URLs, deliberately. {@code m2-plan.md} decides that this
 * milestone does not route by organisation handle, which is what lets M1a's two deferrals
 * — reserved handles, and redirects for retired ones — stay deferred.
 */
@RestController
@RequestMapping("/api/projects")
class ProjectController {

	private final ProjectService projects;

	ProjectController(ProjectService projects) {
		this.projects = projects;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ProjectResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
			@Valid @RequestBody CreateProjectRequest request) {
		return ProjectResponse
			.of(this.projects.create(caller.userId(), caller.tenantId(), request.name(), request.description()));
	}

	/**
	 * The plans in use, or — asked for explicitly — the ones put away.
	 *
	 * <p>
	 * Not paginated, and that is a decision rather than an omission: a plan is bounded at
	 * 500 items and an organisation holds fewer projects than that, so one response is
	 * always enough and there is no cursor API for later work to be designed around.
	 */
	@GetMapping
	List<ProjectResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
			@RequestParam(defaultValue = "false") boolean archived) {
		return this.projects.list(caller.userId(), caller.tenantId(), archived)
			.stream()
			.map(ProjectResponse::of)
			.toList();
	}

	/**
	 * One plan. An identifier belonging to another organisation answers exactly as one
	 * that never existed does.
	 */
	@GetMapping("/{projectId}")
	ProjectResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		return ProjectResponse.of(this.projects.get(caller.userId(), caller.tenantId(), projectId));
	}

	@PatchMapping("/{projectId}")
	ProjectResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
			@Valid @RequestBody UpdateProjectRequest request) {
		return ProjectResponse.of(this.projects.update(caller.userId(), caller.tenantId(), projectId, request.name(),
				request.description()));
	}

	/**
	 * Puts a plan away. A {@code POST} rather than a {@code DELETE} because nothing is
	 * deleted — the row stays, and the estimates that will hang off it stay with it.
	 */
	@PostMapping("/{projectId}/archive")
	ProjectResponse archive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		return ProjectResponse.of(this.projects.setArchived(caller.userId(), caller.tenantId(), projectId, true));
	}

	@PostMapping("/{projectId}/unarchive")
	ProjectResponse unarchive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId) {
		return ProjectResponse.of(this.projects.setArchived(caller.userId(), caller.tenantId(), projectId, false));
	}

}
