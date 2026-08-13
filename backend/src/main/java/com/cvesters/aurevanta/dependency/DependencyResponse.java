package com.cvesters.aurevanta.dependency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One edge, as the API describes it.
 *
 * <p>
 * Names no project, unlike {@code WorkItemResponse}: an edge is only ever listed by plan
 * and only ever addressed by its own identifier, so there is no request whose answer
 * would be ambiguous without it — and its two ends already say which plan it is in.
 */
public record DependencyResponse(UUID id, UUID predecessorItemId, UUID successorItemId, BigDecimal lagHours,
		Instant createdAt) {

	public static DependencyResponse of(Dependency dependency) {
		return new DependencyResponse(dependency.getId(), dependency.getPredecessor().getId(),
				dependency.getSuccessor().getId(), dependency.getLagHours(), dependency.getCreatedAt());
	}

}
