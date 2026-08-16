package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

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
 * have to take on trust — and published as a <em>string</em>, because it is sixty-four
 * bits and a JSON number is a double in a browser. Written as a number, nearly every seed
 * arrived at a client silently rounded, and a seed that is nearly right reproduces
 * nothing at all: the one field that exists to make a run checkable was uncheckable.
 * @param itemCount and {@code estimatedItemCount} are coverage as it was at the moment of
 * the run, which is not necessarily as it is now.
 * @param teamFactorWorseByPercent and the two ends of {@code scopeGrowth}, sent back
 * because what produced a number has to travel with it. They are also what tells two runs
 * of one plan apart: a band that moved because somebody answered these differently is not
 * a plan that slipped, and M10 compares them precisely so that it does not say otherwise.
 */
public record ForecastResponse(UUID id, UUID projectId, Instant createdAt, UUID requestedById, String requestedByName,
		int capacity, int sampleCount, BigDecimal teamFactorWorseByPercent, BigDecimal scopeGrowthP10Percent,
		BigDecimal scopeGrowthP90Percent, @JsonFormat(shape = JsonFormat.Shape.STRING) long seed, int engineVersion,
		String priorityRule, int itemCount, int estimatedItemCount, BigDecimal meanHours, BigDecimal p10Hours,
		BigDecimal p50Hours, BigDecimal p80Hours, BigDecimal p90Hours, BigDecimal p95Hours,
		List<ForecastLimitation> limitations, Histogram histogram) {

	static ForecastResponse of(ForecastRun run, ForecastOutputs outputs) {
		return new ForecastResponse(run.getId(), run.getProject().getId(), run.getCreatedAt(),
				run.getRequestedBy().getId(), run.getRequestedBy().getDisplayName(), run.getCapacity(),
				run.getSampleCount(), run.getTeamFactorWorseByPercent(), run.getScopeGrowthP10Percent(),
				run.getScopeGrowthP90Percent(), run.getSeed(), run.getEngineVersion(), run.getPriorityRule(),
				run.getItemCount(), run.getEstimatedItemCount(), run.getMeanHours(), run.getP10Hours(),
				run.getP50Hours(), run.getP80Hours(), run.getP90Hours(), run.getP95Hours(), outputs.limitations(),
				outputs.histogram());
	}

}
