package com.cvesters.aurevanta.project;

import java.time.Instant;
import java.util.UUID;

/**
 * A project as the API describes it.
 *
 * <p>
 * Carries no organisation, because there is only one it could name: every endpoint that
 * returns this took the tenant from the caller's own token, so repeating it would be the
 * API telling a caller something they gave it.
 *
 * @param archivedAt null while the project is in use, which is also how a client tells
 * the two states apart without a second field that could disagree with this one.
 * @param itemCount how much work the plan holds, archived items excluded.
 * @param estimatedItemCount how much of that carries an estimate. The pair is what lets a
 * screen say "12 of 30 items estimated" without loading the plan — which is the point:
 * coverage is reported prominently rather than left for somebody to work out, because a
 * forecast that quietly covers less than the plan is the failure this product exists to
 * prevent.
 */
public record ProjectResponse(UUID id, String name, String description, Instant createdAt, Instant archivedAt,
		long itemCount, long estimatedItemCount) {

	public static ProjectResponse of(PlannedProject planned) {
		Project project = planned.project();
		return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getCreatedAt(),
				project.getArchivedAt(), planned.itemCount(), planned.estimatedItemCount());
	}

}
