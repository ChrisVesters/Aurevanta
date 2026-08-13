package com.cvesters.aurevanta.item;

import java.time.Instant;
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
 */
public record WorkItemResponse(UUID id, UUID projectId, String title, String description, Instant createdAt,
		Instant archivedAt) {

	public static WorkItemResponse of(WorkItem item) {
		return new WorkItemResponse(item.getId(), item.getProject().getId(), item.getTitle(), item.getDescription(),
				item.getCreatedAt(), item.getArchivedAt());
	}

}
