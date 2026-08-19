package com.cvesters.aurevanta.forecast;

import java.util.ArrayList;
import java.util.Map;

import com.cvesters.aurevanta.forecast.ForecastInputs.PlannedPool;
import com.cvesters.aurevanta.resource.Resource;

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
 * invisible in the way this work's own warning describes — <em>a date is the first thing
 * this product emits that looks like a fact</em>.
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
 * a plan that slipped, and the reporting layer compares them precisely so that it does
 * not say otherwise.
 * @param startsOn and {@code workingHoursPerDay} and {@code calendarRule} are the same
 * thing for the dates: three runs of one plan under three working days are three readings
 * rather than a date moving. Absent together, for a run made before a calendar existed.
 * @param resources the team this run was scheduled against, in the order it was declared
 * — empty for a run made by an organisation that had described none, which is what
 * {@code capacity} still answers for. <strong>It is the run's own team and not
 * today's</strong>, for the reason the calendar is the run's own: reading an old number
 * beside a team it never had is how a tool reports a plan moving when what moved was the
 * question.
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
		LocalDate p90Date, LocalDate p95Date, List<ForecastResourceResponse> resources,
		List<ForecastLimitation> limitations, Histogram histogram) {

	/**
	 * @param pools the organisation's resources by identifier, live and put away alike —
	 * one lookup serving however many runs are being described, which is what makes a
	 * listing of a plan's whole history one query rather than one per row
	 */
	static ForecastResponse of(ForecastRun run, ForecastOutputs outputs, Map<UUID, Resource> pools) {
		List<ForecastResourceResponse> resources = team(run, pools);
		return new ForecastResponse(run.getId(), run.getProject().getId(), run.getCreatedAt(),
				run.getRequestedBy().getId(), run.getRequestedBy().getDisplayName(), run.getCapacity(),
				run.getSampleCount(), run.getTeamFactorWorseByPercent(), run.getScopeGrowthP10Percent(),
				run.getScopeGrowthP90Percent(), run.getStartsOn(), run.getWorkingHoursPerDay(), run.getCalendarRule(),
				run.getSeed(), run.getEngineVersion(), run.getPriorityRule(), run.getItemCount(),
				run.getEstimatedItemCount(), run.getMeanHours(), run.getP10Hours(), run.getP50Hours(),
				run.getP80Hours(), run.getP90Hours(), run.getP95Hours(), dateOf(run, run.getP10Hours()),
				dateOf(run, run.getP50Hours()), dateOf(run, run.getP80Hours()), dateOf(run, run.getP90Hours()),
				dateOf(run, run.getP95Hours()), resources, outputs.limitations(), outputs.histogram());
	}

	/**
	 * The team a run was scheduled against, with today's names on it.
	 *
	 * <p>
	 * <strong>The units come off the run and the names off the organisation</strong>,
	 * which is the split a contribution ranking already makes for the work it names: what
	 * a pool was called is not a thing that moved, and what it held then is. A pool put
	 * away since is marked; one this organisation no longer holds at all says so rather
	 * than rendering as a blank.
	 */
	private static List<ForecastResourceResponse> team(ForecastRun run, Map<UUID, Resource> pools) {
		if (run.getResourcing() == null) {
			return List.of();
		}
		PlannedPool[] declared = ForecastSnapshots.read(run.getResourcing(), PlannedPool[].class);
		List<ForecastResourceResponse> described = new ArrayList<>(declared.length);
		for (PlannedPool pool : declared) {
			described.add(ForecastResourceResponse.of(pool.resourceId(), pool.units(), pools.get(pool.resourceId())));
		}
		return described;
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
	 *
	 * <p>
	 * Reachable from the package rather than private, because a drift over a history
	 * reads the same days off the same rows and two ways of resolving one would be one
	 * right way and one that had drifted — which is the whole hazard a stored rule name
	 * exists to close.
	 */
	static LocalDate dateOf(ForecastRun run, BigDecimal hours) {
		if (!run.hasReadableCalendar()) {
			return null;
		}
		return WorkingCalendar.finishOn(run.getStartsOn(), hours, run.getWorkingHoursPerDay());
	}

}
