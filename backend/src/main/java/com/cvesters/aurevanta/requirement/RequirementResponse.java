package com.cvesters.aurevanta.requirement;

import java.util.UUID;

/**
 * One thing a piece of work needs, as the API describes it.
 *
 * <p>
 * Carries the pool's name beside its identifier, so a screen drawing five hundred rows
 * does not have to hold the whole team to label any of them — which is the same reason a
 * contribution carries the title of the work it ranks.
 *
 * @param resourceArchived whether that pool has since been put away. A plan may still say
 * a task needs something the team no longer has, and a reader is better told that than
 * shown a name that quietly means nothing.
 */
public record RequirementResponse(UUID workItemId, UUID resourceId, String resourceName, boolean resourceArchived,
		int units) {

	public static RequirementResponse of(Requirement requirement) {
		return new RequirementResponse(requirement.getWorkItem().getId(), requirement.getResource().getId(),
				requirement.getResource().getName(), requirement.getResource().getArchivedAt() != null,
				requirement.getUnits());
	}

}
