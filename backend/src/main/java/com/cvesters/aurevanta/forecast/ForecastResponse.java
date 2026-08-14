package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cvesters.aurevanta.forecast.model.Histogram;

/**
 * One forecast as the API describes it: the answer, what was assumed to reach it, and
 * what the model did not do.
 *
 * <p>
 * <strong>Hours, never dates.</strong> Turning effort into a calendar needs an assumption
 * about what a working day is worth, and M4 is where that gets made where somebody can
 * see it.
 *
 * <p>
 * <strong>The assumptions travel with the number, and so do the limitations.</strong> A
 * band without them is this product's own failure mode with a chart on it — and the
 * limitations are the part most likely to be dropped as "we will add the caveat later",
 * which is the one thing that cannot go in later, because a number seen without its
 * caveat is already in somebody's slide.
 *
 * @param seed with the inputs and the engine version, the whole of what makes this run
 * reproducible. Published because a forecast somebody cannot check is a forecast they
 * have to take on trust.
 * @param itemCount and {@code estimatedItemCount} are coverage as it was at the moment of
 * the run, which is not necessarily as it is now.
 */
public record ForecastResponse(UUID id, UUID projectId, Instant createdAt, UUID requestedById, String requestedByName,
		int capacity, int sampleCount, long seed, int engineVersion, String priorityRule, int itemCount,
		int estimatedItemCount, BigDecimal meanHours, BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p80Hours,
		BigDecimal p90Hours, BigDecimal p95Hours, List<ForecastLimitation> limitations, Histogram histogram) {

	static ForecastResponse of(ForecastRun run, ForecastOutputs outputs) {
		return new ForecastResponse(run.getId(), run.getProject().getId(), run.getCreatedAt(),
				run.getRequestedBy().getId(), run.getRequestedBy().getDisplayName(), run.getCapacity(),
				run.getSampleCount(), run.getSeed(), run.getEngineVersion(), run.getPriorityRule(), run.getItemCount(),
				run.getEstimatedItemCount(), run.getMeanHours(), run.getP10Hours(), run.getP50Hours(),
				run.getP80Hours(), run.getP90Hours(), run.getP95Hours(), outputs.limitations(), outputs.histogram());
	}

}
