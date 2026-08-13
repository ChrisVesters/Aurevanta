package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One current estimate, as the API describes it.
 *
 * <p>
 * Names its estimator, and by name as well as identifier: several people may hold a
 * current estimate on one item, so a reader has to be able to tell whose is whose — and
 * the point of keeping several is that their disagreement is signal. Nothing is disclosed
 * by it that a member could not already read from {@code /api/members}.
 *
 * @param createdAt when this range was given, which is the whole reason the row exists. A
 * later one does not replace it; it merely becomes the current one.
 */
public record EstimateResponse(UUID id, UUID itemId, UUID estimatorId, String estimatorName, BigDecimal p10Hours,
		BigDecimal p50Hours, BigDecimal p90Hours, Instant createdAt) {

	public static EstimateResponse of(Estimate estimate) {
		return new EstimateResponse(estimate.getId(), estimate.getWorkItem().getId(), estimate.getEstimator().getId(),
				estimate.getEstimator().getDisplayName(), estimate.getP10Hours(), estimate.getP50Hours(),
				estimate.getP90Hours(), estimate.getCreatedAt());
	}

}
