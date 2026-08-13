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
 */
public record ProjectResponse(UUID id, String name, String description, Instant createdAt, Instant archivedAt) {

	public static ProjectResponse of(Project project) {
		return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), project.getCreatedAt(),
				project.getArchivedAt());
	}

}
