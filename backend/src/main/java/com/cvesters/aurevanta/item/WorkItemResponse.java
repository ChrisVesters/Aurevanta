package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A work item as the API describes it.
 *
 * <p>
 * Carries its project, unlike {@code ProjectResponse}, which carries no organisation. The
 * asymmetry is not an oversight: an organisation is the one the caller's token already
 * names, and a project is not — {@code PATCH /api/items/{id}} names no project in its
 * path, so without this a client renaming an item would have nothing to say which plan it
 * had just changed.
 *
 * @param archivedAt null while the item is in use, which is how a client tells the two
 * states apart without a second field that could disagree with this one.
 * @param startedOn and {@code completedOn} are dates rather than moments, because they
 * are what somebody reports about a day rather than something the server observed.
 * @param actualEffortHours null wherever nobody measured it, which will be most of the
 * time — and comparable directly with an estimate, since both are hours of effort.
 */
public record WorkItemResponse(UUID id, UUID projectId, String title, String description, Instant createdAt,
		Instant archivedAt, WorkItemStatus status, LocalDate startedOn, LocalDate completedOn,
		BigDecimal actualEffortHours) {

	public static WorkItemResponse of(WorkItem item) {
		return new WorkItemResponse(item.getId(), item.getProject().getId(), item.getTitle(), item.getDescription(),
				item.getCreatedAt(), item.getArchivedAt(), item.getStatus(), item.getStartedOn(), item.getCompletedOn(),
				item.getActualEffortHours());
	}

}
