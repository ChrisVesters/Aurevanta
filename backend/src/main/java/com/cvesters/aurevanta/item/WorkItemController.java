package com.cvesters.aurevanta.item;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * The work inside a plan.
 *
 * <p>
 * <strong>No class-level path, because these endpoints sit at two.</strong> An item is
 * created and listed <em>within</em> a project, which is the only moment its plan has to
 * be named; once it exists it is addressed on its own at {@code /api/items/{id}}.
 * Estimates (step 3) and dependencies (step 5) address items directly too, so a path that
 * repeated the project would be a second identifier to check against the first — and a
 * mismatch between them would be a refusal nobody could act on.
 *
 * <p>
 * Every one of them is reachable by every member, and the organisation comes from the
 * caller's token in all of them.
 */
@RestController
class WorkItemController {

	private final WorkItemService items;

	WorkItemController(WorkItemService items) {
		this.items = items;
	}

	@PostMapping("/api/projects/{projectId}/items")
	@ResponseStatus(HttpStatus.CREATED)
	WorkItemResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
			@Valid @RequestBody CreateWorkItemRequest request) {
		return WorkItemResponse.of(this.items.create(caller.userId(), caller.tenantId(), projectId, request.title(),
				request.description()));
	}

	/**
	 * The work in one plan, in the order it was written down. Not paginated, because 500
	 * items to a project is the ceiling this milestone fixed so that it need not be.
	 */
	@GetMapping("/api/projects/{projectId}/items")
	List<WorkItemResponse> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
			@RequestParam(defaultValue = "false") boolean archived) {
		return this.items.list(caller.userId(), caller.tenantId(), projectId, archived)
			.stream()
			.map(WorkItemResponse::of)
			.toList();
	}

	@PatchMapping("/api/items/{itemId}")
	WorkItemResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId,
			@Valid @RequestBody UpdateWorkItemRequest request) {
		return WorkItemResponse
			.of(this.items.update(caller.userId(), caller.tenantId(), itemId, request.title(), request.description()));
	}

	/**
	 * Records what has already happened to a piece of work.
	 *
	 * <p>
	 * Its own endpoint rather than more fields on {@code PATCH /api/items/{id}}, because
	 * the two are different acts by different people at different moments: rewording a
	 * task is planning, and saying it finished on Tuesday is reporting. Keeping them
	 * apart also keeps a rename from having to carry — and so being able to overwrite —
	 * the dates M8 and M10 read.
	 */
	@PatchMapping("/api/items/{itemId}/progress")
	WorkItemResponse recordProgress(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId,
			@Valid @RequestBody UpdateProgressRequest request) {
		return WorkItemResponse.of(this.items.recordProgress(caller.userId(), caller.tenantId(), itemId,
				request.status(), request.startedOn(), request.completedOn(), request.actualEffortHours()));
	}

	/**
	 * Everything anybody has ever claimed about one piece of work, newest first.
	 *
	 * <p>
	 * The same path as the {@code PATCH} above and the natural resource beside it: that
	 * one adds a claim and this one reads the claims. Nothing modifies or removes one —
	 * the whole value of the table is that a report cannot be taken back, only added to.
	 *
	 * <p>
	 * It answers with more than the one line on screen needs. The resource is the log,
	 * and an endpoint that returned only the newest would need a rule about how much
	 * history is worth serving — which is the question this table exists to stop anybody
	 * answering by accident.
	 */
	@GetMapping("/api/items/{itemId}/progress")
	List<ProgressReportResponse> progress(@AuthenticationPrincipal AuthenticatedUser caller,
			@PathVariable UUID itemId) {
		return this.items.progressOf(caller.userId(), caller.tenantId(), itemId)
			.stream()
			.map(ProgressReportResponse::of)
			.toList();
	}

	/**
	 * Puts an item away. A {@code POST} rather than a {@code DELETE}, because nothing is
	 * deleted — and from step 3 this row is what an estimate hangs off.
	 */
	@PostMapping("/api/items/{itemId}/archive")
	WorkItemResponse archive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId) {
		return WorkItemResponse.of(this.items.setArchived(caller.userId(), caller.tenantId(), itemId, true));
	}

	@PostMapping("/api/items/{itemId}/unarchive")
	WorkItemResponse unarchive(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID itemId) {
		return WorkItemResponse.of(this.items.setArchived(caller.userId(), caller.tenantId(), itemId, false));
	}

}
