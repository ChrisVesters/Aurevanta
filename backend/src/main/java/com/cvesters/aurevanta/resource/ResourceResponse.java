package com.cvesters.aurevanta.resource;

import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.user.User;

/**
 * A pool as the API describes it.
 *
 * <p>
 * Carries no organisation, because there is only one it could name — every endpoint that
 * returns this took the tenant from the caller's own token.
 *
 * @param archivedAt null while the pool is in use, which is how a client tells the two
 * states apart without a second field that could disagree with this one.
 * @param personId and {@code personName} are absent together, and the name is here so
 * that a screen showing a team does not have to ask who everybody is a second time. It is
 * <em>not</em> a report about that person: a pool says a team has somebody in it, and
 * nothing anywhere says what they are working on.
 */
public record ResourceResponse(UUID id, String name, int units, UUID personId, String personName, Instant createdAt,
		Instant archivedAt) {

	public static ResourceResponse of(Resource resource) {
		User person = resource.getPerson();
		return new ResourceResponse(resource.getId(), resource.getName(), resource.getUnits(),
				(person != null) ? person.getId() : null, (person != null) ? person.getDisplayName() : null,
				resource.getCreatedAt(), resource.getArchivedAt());
	}

}
