package com.cvesters.aurevanta.requirement;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.requirement.RequirementService.RequiredUnits;
import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * What the work in a plan needs.
 *
 * <p>
 * <strong>Three paths and no class-level one</strong>, following
 * {@code WorkItemController}: what one item needs is addressed at the item, and what a
 * whole plan needs is addressed at the plan — because a screen showing a plan needs every
 * line at once and asking per item would be five hundred requests to draw one page, which
 * is the rule {@code estimate} already keeps.
 *
 * <p>
 * <strong>The only {@code PUT} in this application, and it earns the verb.</strong>
 * Everything else creates one row, changes one row, or moves one row's state, and those
 * are a {@code POST} and a {@code PATCH}. This replaces a whole set with the set in the
 * body, which is what a {@code PUT} means — and the alternative, three endpoints to add,
 * change and remove a line, would make "it needs these two things" a sequence a reader
 * has to reassemble rather than a fact arriving once.
 *
 * <p>
 * Reachable by every member, with the organisation coming from the caller's token.
 */
@RestController
class RequirementController {

	private final RequirementService requirements;

	RequirementController(RequirementService requirements) {
		this.requirements = requirements;
	}

	@GetMapping("/api/items/{itemId}/requirements")
	List<RequirementResponse> forItem(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId) {
		return this.requirements.listForItem(caller.userId(), caller.tenantId(), itemId)
			.stream()
			.map(RequirementResponse::of)
			.toList();
	}

	@GetMapping("/api/projects/{projectId}/requirements")
	List<RequirementResponse> inProject(@AuthenticationPrincipal AuthenticatedUser caller,
			@PathVariable UUID projectId) {
		return this.requirements.listInProject(caller.userId(), caller.tenantId(), projectId)
			.stream()
			.map(RequirementResponse::of)
			.toList();
	}

	@PutMapping("/api/items/{itemId}/requirements")
	List<RequirementResponse> replace(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId,
			@Valid @RequestBody SetRequirementsRequest request) {
		List<RequiredUnits> wanted = request.needs()
			.stream()
			.map((need) -> new RequiredUnits(need.resourceId(), need.units()))
			.toList();
		return this.requirements.replaceForItem(caller.userId(), caller.tenantId(), itemId, wanted)
			.stream()
			.map(RequirementResponse::of)
			.toList();
	}

}
