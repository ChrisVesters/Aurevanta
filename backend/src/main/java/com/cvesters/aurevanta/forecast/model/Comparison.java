package com.cvesters.aurevanta.forecast.model;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * What two forecasts of one plan share, and what they do not.
 *
 * <p>
 * <strong>Everything downstream of two runs rests on this.</strong> A date that moved
 * because the engine changed, or because the working day was adjusted, or because
 * somebody halved the capacity, has not moved for a reason worth telling anybody — and a
 * feature that reports it as a slide is worse than one that reports nothing.
 *
 * <p>
 * <strong>An engine version differing is a refusal and everything else is a
 * finding</strong>, and the two look contradictory until the question is put properly.
 * The contribution ranking refuses to explain a run it cannot reproduce, because there is
 * nothing to compare *with*: a ranking from a different model is an exact ranking of a
 * plan nobody forecast. That is what an {@code Engine.VERSION} difference is. Everything
 * else here is the *question* changing, which is precisely the thing worth reporting —
 * refusing a pair because somebody adjusted the capacity would leave them staring at two
 * dates a fortnight apart with no account of either.
 *
 * <p>
 * <strong>Compared with {@code compareTo} and never {@code equals}</strong>, because
 * these are {@link BigDecimal}s: {@code 30} and {@code 30.00} are the same assumption and
 * are not equal objects. Both of these arrive from {@code numeric} columns and so agree
 * on scale in practice, which is exactly what makes the bug the kind that survives every
 * test until one value comes from somewhere else.
 */
public record Comparison(Set<Difference> differences) {

	/** One way two runs can have been asked different questions. */
	public enum Difference {

		/**
		 * The model itself changed. Not a difference to report but a reason there is
		 * nothing to compare — see the class note.
		 */
		ENGINE_VERSION,

		/**
		 * Which order the scheduler ran ready work in, and the second of the two that
		 * refuse rather than report.
		 *
		 * <p>
		 * <strong>It sits with the engine version and not with the calendar, though it is
		 * a stored rule name like one.</strong> A calendar is laid over an answer the
		 * engine has already given, so two runs read under two calendars are one answer
		 * read twice; a priority rule is <em>inside</em> the scheduler, so two runs under
		 * two rules are two answers. There is one rule today, which is exactly why this
		 * is here — the day a second ships, every plan re-forecast across the change
		 * would otherwise report the jump as drift, which is `roadmap.md`'s slide that
		 * never happened.
		 */
		PRIORITY_RULE,

		/**
		 * Which calendar the hours were read through. Two defensible ones give two dates.
		 */
		CALENDAR_RULE,

		/** What one worker's day was said to hold. */
		WORKING_DAY,

		/** How many pieces of work could be under way at once. */
		CAPACITY,

		/**
		 * Which pools there were, and how many units each held.
		 *
		 * <p>
		 * <strong>Its own difference rather than part of the capacity</strong>, because a
		 * team can be reshaped without changing size: three backend and three frontend
		 * becoming two and four holds the capacity still and moves the date, and a
		 * comparison that saw only the total would report that as a plan sliding.
		 */
		RESOURCES,

		/** How much longer everything was assumed to take in a bad stretch. */
		TEAM_FACTOR,

		/** Either end of how much more work the plan was assumed to turn out to hold. */
		SCOPE_GROWTH,

		/**
		 * The day work was said to begin — time simply passing, rather than a judgement.
		 */
		STARTS_ON

	}

	private static final Set<Difference> CALENDAR = EnumSet.of(Difference.CALENDAR_RULE, Difference.WORKING_DAY);

	private static final Set<Difference> ASSUMPTIONS = EnumSet.of(Difference.CAPACITY, Difference.RESOURCES,
			Difference.TEAM_FACTOR, Difference.SCOPE_GROWTH);

	private static final Set<Difference> MODEL = EnumSet.of(Difference.ENGINE_VERSION, Difference.PRIORITY_RULE);

	public Comparison {
		differences = Set.copyOf(differences);
	}

	/** Everything the two disagree about, which for most pairs is nothing. */
	public static Comparison between(ForecastTerms older, ForecastTerms newer) {
		Set<Difference> found = EnumSet.noneOf(Difference.class);
		if (older.engineVersion() != newer.engineVersion()) {
			found.add(Difference.ENGINE_VERSION);
		}
		if (!Objects.equals(older.priorityRule(), newer.priorityRule())) {
			found.add(Difference.PRIORITY_RULE);
		}
		if (!Objects.equals(older.calendarRule(), newer.calendarRule())) {
			found.add(Difference.CALENDAR_RULE);
		}
		if (differs(older.workingHoursPerDay(), newer.workingHoursPerDay())) {
			found.add(Difference.WORKING_DAY);
		}
		if (older.capacity() != newer.capacity()) {
			found.add(Difference.CAPACITY);
		}
		if (!Objects.equals(older.resourcing(), newer.resourcing())) {
			found.add(Difference.RESOURCES);
		}
		if (differs(older.teamFactorWorseByPercent(), newer.teamFactorWorseByPercent())) {
			found.add(Difference.TEAM_FACTOR);
		}
		if (differs(older.scopeGrowthP10Percent(), newer.scopeGrowthP10Percent())
				|| differs(older.scopeGrowthP90Percent(), newer.scopeGrowthP90Percent())) {
			found.add(Difference.SCOPE_GROWTH);
		}
		if (!Objects.equals(older.startsOn(), newer.startsOn())) {
			found.add(Difference.STARTS_ON);
		}
		return new Comparison(found);
	}

	/**
	 * Whether these two can be set beside each other at all.
	 *
	 * <p>
	 * False only where the <em>model</em> differed — the engine version or the priority
	 * rule. Everything else the two disagree about is something to say rather than a
	 * reason to say nothing.
	 */
	public boolean comparable() {
		return noneOf(MODEL);
	}

	/**
	 * Whether their dates are on one scale.
	 *
	 * <p>
	 * When they are not, the two are still comparable — in <em>hours</em>, which is what
	 * the engine produced and what no calendar has been laid over yet. A difference in
	 * days between runs read under different working days is a difference in the divisor,
	 * and reporting it as a plan moving is the calendar's own error arriving one level
	 * up.
	 */
	public boolean sameCalendar() {
		return noneOf(CALENDAR);
	}

	/** Whether the same question was asked of the model — capacity and the two ranges. */
	public boolean sameAssumptions() {
		return noneOf(ASSUMPTIONS);
	}

	/**
	 * Whether both were started from the same day.
	 *
	 * <p>
	 * Its own question rather than one of the assumptions above, because it is the one
	 * that is nobody's doing: a plan started a month later finishes a month later. A
	 * decomposition applies it last for that reason.
	 *
	 * <p>
	 * <strong>Nothing calls this, and that is what it is for.</strong> {@code
	 * Drift.sameQuestion} is {@code comparable() && sameCalendar() && sameAssumptions()}
	 * — this list exactly, minus this one — and a reader checking that expression against
	 * the type has to be able to see what was left out. Delete it and the omission stops
	 * being visible: the expression reads as complete, and the next person to "finish" it
	 * ends every drift window at one run, because a plan re-forecast weekly is started
	 * from today every time. The rule it guards is argued where it is broken, not here.
	 */
	public boolean sameStart() {
		return !this.differences.contains(Difference.STARTS_ON);
	}

	private boolean noneOf(Set<Difference> group) {
		for (Difference difference : group) {
			if (this.differences.contains(difference)) {
				return false;
			}
		}
		return true;
	}

	private static boolean differs(BigDecimal older, BigDecimal newer) {
		if (older == null || newer == null) {
			return older != newer;
		}
		return older.compareTo(newer) != 0;
	}

}
