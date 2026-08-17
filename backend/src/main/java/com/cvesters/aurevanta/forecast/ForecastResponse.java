package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.cvesters.aurevanta.forecast.model.Histogram;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;

/**
 * One forecast as the API describes it: the answer, what was assumed to reach it, and
 * what the model did not do.
 *
 * <p>
 * <strong>Hours and dates, and the hours are not for decoration.</strong> The band is
 * what the engine produced; a date is one percentile of it with a working day laid on
 * top, and that assumption is exactly the kind that gets forgotten. Removing the hours
 * would leave nothing here that came out of the model and would make the working day
 * invisible in the way this milestone's own warning describes — <em>a date is the first
 * thing this product emits that looks like a fact</em>.
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
 * @param startsOn and {@code workingHoursPerDay} and {@code calendarRule} are the same
 * thing for the dates: three runs of one plan under three working days are three readings
 * rather than a date moving. Absent together, for a run made before a calendar existed.
 * @param p10Date and the four beside it are {@link #p10Hours} and its neighbours resolved
 * through {@link WorkingCalendar} — derived here rather than stored, because a date costs
 * a division and a walk over some weekends where a percentile costs ten thousand
 * simulations. Absent whenever the calendar is, and absent as well under a rule this code
 * cannot resolve.
 */
public record ForecastResponse(UUID id, UUID projectId, Instant createdAt, UUID requestedById, String requestedByName,
		int capacity, int sampleCount, BigDecimal teamFactorWorseByPercent, BigDecimal scopeGrowthP10Percent,
		BigDecimal scopeGrowthP90Percent, LocalDate startsOn, BigDecimal workingHoursPerDay, String calendarRule,
		@JsonFormat(shape = JsonFormat.Shape.STRING) long seed, int engineVersion, String priorityRule, int itemCount,
		int estimatedItemCount, BigDecimal meanHours, BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p80Hours,
		BigDecimal p90Hours, BigDecimal p95Hours, LocalDate p10Date, LocalDate p50Date, LocalDate p80Date,
		LocalDate p90Date, LocalDate p95Date, List<ForecastLimitation> limitations, Histogram histogram) {

	static ForecastResponse of(ForecastRun run, ForecastOutputs outputs) {
		return new ForecastResponse(run.getId(), run.getProject().getId(), run.getCreatedAt(),
				run.getRequestedBy().getId(), run.getRequestedBy().getDisplayName(), run.getCapacity(),
				run.getSampleCount(), run.getTeamFactorWorseByPercent(), run.getScopeGrowthP10Percent(),
				run.getScopeGrowthP90Percent(), run.getStartsOn(), run.getWorkingHoursPerDay(), run.getCalendarRule(),
				run.getSeed(), run.getEngineVersion(), run.getPriorityRule(), run.getItemCount(),
				run.getEstimatedItemCount(), run.getMeanHours(), run.getP10Hours(), run.getP50Hours(),
				run.getP80Hours(), run.getP90Hours(), run.getP95Hours(), dateOf(run, run.getP10Hours()),
				dateOf(run, run.getP50Hours()), dateOf(run, run.getP80Hours()), dateOf(run, run.getP90Hours()),
				dateOf(run, run.getP95Hours()), outputs.limitations(), outputs.histogram());
	}

	/**
	 * One percentile's hours as a day, or nothing at all.
	 *
	 * <p>
	 * <strong>The stored rule decides, not the presence of the other two
	 * columns.</strong> A run resolves under the calendar it was made with; a run made
	 * under a rule this code does not implement reports its hours, its own rule's name
	 * and no dates, rather than being handed five days worked out by a calendar it never
	 * assumed. That is the whole reason the rule is a name — the alternative silently
	 * reads history under today's rule and is indistinguishable from a plan that moved.
	 *
	 * <p>
	 * There is exactly one rule today, so the branch that refuses is reachable only from
	 * a row this application did not write. It is here because the day a second rule
	 * exists is the day it would otherwise start lying about every run made before it,
	 * quietly.
	 *
	 * <p>
	 * The mean deliberately gets no date. It is not a percentile, so no confidence can be
	 * put against it, and a date nobody can name a confidence for is the one number on
	 * this response somebody could act on without knowing what it claims.
	 */
	private static LocalDate dateOf(ForecastRun run, BigDecimal hours) {
		if (!run.hasReadableCalendar()) {
			return null;
		}
		return WorkingCalendar.finishOn(run.getStartsOn(), hours, run.getWorkingHoursPerDay());
	}

}
