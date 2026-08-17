package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.forecast.model.EstimateQuality;

/**
 * One current estimate, as the API describes it.
 *
 * <p>
 * Names its estimator, and by name as well as identifier: several people may hold a
 * current estimate on one item, so a reader has to be able to tell whose is whose — and
 * the point of keeping several is that their disagreement is signal. Nothing is disclosed
 * by it that a member could not already read from {@code /api/members}.
 *
 * <p>
 * <strong>This is the one place a domain package reaches into {@code forecast.model}, and
 * it does not reverse an arrow.</strong> The rule the domain packages keep is that no
 * <em>feature</em> depends on a feature that depends on it, and {@code forecast.model} is
 * not one: it has no entity, no repository, no service and no controller, it holds no
 * Spring, no JPA and no I/O, and it imports nothing from this codebase, so it cannot be
 * part of a cycle in it. It is used here the way {@code java.lang.Math} is.
 * {@code estimate} gains no visibility of a run, a capacity or a seed, and
 * {@code forecast} the feature still points one way.
 *
 * <p>
 * The alternative was for the browser to decide whether a range is worth questioning,
 * which would be two rules about one estimate that can disagree — the thing
 * {@code m3a-plan.md} rejected in advance and by name when it handed this over.
 *
 * @param createdAt when this range was given, which is the whole reason the row exists. A
 * later one does not replace it; it merely becomes the current one.
 * @param consistency how far the stated middle sits from the one the two ends imply.
 * Derived on the way out and never stored: it is arithmetic over three columns and one
 * constant, so keeping it would freeze today's threshold into rows that outlive it.
 * @param inconsistent and {@code overconfident} are what a screen renders. They are
 * advice and never a refusal — the estimate is stored exactly as given, flagged or not.
 */
public record EstimateResponse(UUID id, UUID itemId, UUID estimatorId, String estimatorName, BigDecimal p10Hours,
		BigDecimal p50Hours, BigDecimal p90Hours, double consistency, boolean inconsistent, boolean overconfident,
		Instant createdAt) {

	public static EstimateResponse of(Estimate estimate) {
		EstimateQuality quality = EstimateQuality.of(estimate.getP10Hours().doubleValue(),
				estimate.getP50Hours().doubleValue(), estimate.getP90Hours().doubleValue());
		return new EstimateResponse(estimate.getId(), estimate.getWorkItem().getId(), estimate.getEstimator().getId(),
				estimate.getEstimator().getDisplayName(), estimate.getP10Hours(), estimate.getP50Hours(),
				estimate.getP90Hours(), quality.consistency(), quality.inconsistent(), quality.overconfident(),
				estimate.getCreatedAt());
	}

}
