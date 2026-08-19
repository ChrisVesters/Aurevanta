package com.cvesters.aurevanta.resource;

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
 * The pools one organisation says it has.
 *
 * <p>
 * <strong>Addressed like a plan and not like a person.</strong> These are
 * organisation-wide rather than per-project, because a team is not a property of one plan
 * — the same three backend engineers are the constraint on every plan at once, and
 * declaring them per plan would be the same claim written down as many times as there are
 * places to get it wrong.
 *
 * <p>
 * Every endpoint here is reachable by every member: roles govern administration, and
 * saying what a team is made of is planning. The organisation comes from the caller's
 * token in all five, and is never named in a path or a body.
 *
 * <p>
 * <strong>Archiving is a {@code POST} and there is no {@code DELETE}</strong>, following
 * {@code ProjectController} exactly: the row stays, because a forecast stored the
 * declaration it was scheduled under and a pool that had vanished would leave that
 * snapshot describing an identifier.
 */
@RestController
@RequestMapping("/api/resources")
class ResourceController {

	private final ResourceService resources;

	ResourceController(ResourceService resources) {
		this.resources = resources;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	ResourceResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
			@Valid @RequestBody CreateResourceRequest request) {
		return ResourceResponse.of(this.resources.create(caller.userId(), caller.tenantId(), request.name(),
				request.units(), request.personId()));
	}

	/**
	 * The pools in use, or — asked for explicitly — the ones put away.
	 *
	 * <p>
	 * In declaration order, which is part of a modelling rule rather than a presentation
	 * choice: an item that names no pool takes one unit of the first with one free, so
	 * this order is what a reader has to be able to see.
	 */
	@GetMapping
	List<ResourceResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
			@RequestParam(defaultValue = "false") boolean archived) {
		return this.resources.list(caller.userId(), caller.tenantId(), archived)
			.stream()
			.map(ResourceResponse::of)
			.toList();
	}

	@PatchMapping("/{resourceId}")
	ResourceResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID resourceId,
			@Valid @RequestBody UpdateResourceRequest request) {
		return ResourceResponse.of(this.resources.update(caller.userId(), caller.tenantId(), resourceId, request.name(),
				request.units(), request.personId()));
	}

	@PostMapping("/{resourceId}/archive")
	ResourceResponse archive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID resourceId) {
		return ResourceResponse.of(this.resources.setArchived(caller.userId(), caller.tenantId(), resourceId, true));
	}

	@PostMapping("/{resourceId}/unarchive")
	ResourceResponse unarchive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID resourceId) {
		return ResourceResponse.of(this.resources.setArchived(caller.userId(), caller.tenantId(), resourceId, false));
	}

}
