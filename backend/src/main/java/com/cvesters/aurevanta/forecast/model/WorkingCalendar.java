package com.cvesters.aurevanta.forecast.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The day a number of hours lands on, given when the work starts and how much of a day
 * one person's day holds.
 *
 * <p>
 * <strong>Beside the engine and not inside it.</strong> {@link Engine} stays in hours
 * from end to end: its output is a distribution over <em>effort</em>, and a date is one
 * presentation of one percentile of it under an assumption the engine never made. Folding
 * a calendar into the sampling loop would put a working day inside ten thousand runs that
 * do not need one, and would make {@link Engine#VERSION} change every time somebody
 * adjusts a holiday — which is a calendar change invalidating a stored forecast's
 * numbers, and it must not.
 *
 * <p>
 * <strong>The working day is one worker's, never the team's, and this is the whole
 * milestone.</strong> {@link Schedule#finish} already ran {@code capacity} items at a
 * time, so the hours that arrive here are a completion <em>time</em> and not a sum of
 * everybody's effort — capacity is inside the number already. Dividing by a team's daily
 * total ("four people at six hours each, so a working day is 24 hours") counts capacity
 * twice and produces a date four times too early, with the band unchanged, the assumption
 * on screen and nothing anywhere looking wrong. That is why nothing in this class has
 * ever heard of how many people there are.
 *
 * <p>
 * <strong>The division is exact because a day boundary is a step.</strong> {@code ceil}
 * turns a smooth quantity into a discrete one, so an error in the last bit of a double is
 * not a rounding difference but a whole day: 20.01 hours at 6.67 a day is exactly 3 in
 * decimal and 3.0000000000000004 in binary, which is four working days rather than three.
 * 6.67 is what somebody types for two thirds of a ten-hour day. Both ends arrive as
 * {@link BigDecimal} already — the percentile from a {@code numeric(14, 2)} column, the
 * working day from a {@code numeric(4, 2)} one — so exact arithmetic costs nothing here
 * and needs no conversion.
 */
public final class WorkingCalendar {

	/**
	 * What the rule below is called, stored on every run that was read under it.
	 *
	 * <p>
	 * A name rather than a boolean, for the reason {@link Schedule#PRIORITY_RULE} is one:
	 * two defensible calendars give two different dates from identical data, so a run
	 * made under one must never be silently compared with a run made under another.
	 * Holidays and per-person availability are a <em>second</em> rule name rather than an
	 * edit to this one, and every run made before them keeps resolving under this.
	 */
	public static final String RULE = "five_day_week";

	/**
	 * Nobody's day holds more than this, whatever they type.
	 *
	 * <p>
	 * Published so that the request taking a working day can refuse at the same bound
	 * rather than at a second copy of the number. The <em>check</em> belongs in both
	 * places — one so the refusal arrives against the box somebody typed in, one so this
	 * function cannot be handed nonsense by a caller that skipped it — but the bound
	 * itself is a fact about days and gets stated once.
	 */
	public static final int LONGEST_DAY_HOURS = 24;

	private static final BigDecimal LONGEST_DAY = BigDecimal.valueOf(LONGEST_DAY_HOURS);

	private static final int WORKING_DAYS_IN_A_WEEK = 5;

	private WorkingCalendar() {
	}

	/**
	 * The day the last of {@code hours} is worked, counting Monday to Friday and nothing
	 * else.
	 *
	 * <p>
	 * Work that has not begun by a working day begins on the next one, so a plan starting
	 * on a Saturday finishes exactly where the same plan starting on the Monday does —
	 * and says so rather than inventing two days of weekend progress.
	 *
	 * <p>
	 * <strong>Whole weeks are counted rather than walked</strong>, so four thousand hours
	 * costs what four do. A walk would be simpler to read and would be run once per
	 * percentile per response, which is cheap enough today; it is not written that way
	 * because the cost is unbounded in the one input a person types freely.
	 * @param startsOn the day work begins, or the weekend before it
	 * @param hours what the engine produced at some percentile, in hours of effort
	 * @param hoursPerDay what <em>one</em> person's working day holds
	 * @throws IllegalArgumentException if the working day is not positive or is longer
	 * than a day holds, or if the hours are negative
	 */
	public static LocalDate finishOn(LocalDate startsOn, BigDecimal hours, BigDecimal hoursPerDay) {
		Objects.requireNonNull(startsOn, "A date has to start somewhere");
		Objects.requireNonNull(hours, "There is no date without an amount of work");
		requireWorkingDay(hoursPerDay);
		if (hours.signum() < 0) {
			throw new IllegalArgumentException("A plan cannot hold less work than none, but held " + hours + " hours");
		}
		LocalDate first = nextWorkingDay(startsOn);
		long days = hours.divide(hoursPerDay, 0, RoundingMode.CEILING).longValueExact();
		if (days == 0) {
			// Nothing left to do, so the answer is the day it would have been done on.
			return first;
		}
		// The first day's work happens on the first day, so it is the days after
		// it that get laid out over the weeks.
		long after = days - 1;
		LocalDate whole = first.plusWeeks(after / WORKING_DAYS_IN_A_WEEK);
		int rest = (int) (after % WORKING_DAYS_IN_A_WEEK);
		boolean overTheWeekend = whole.getDayOfWeek().getValue() + rest > WORKING_DAYS_IN_A_WEEK;
		return whole.plusDays(overTheWeekend ? rest + 2 : rest);
	}

	/**
	 * How much work fits between a start and a day somebody wants it done by — the
	 * inverse of {@link #finishOn}, and the whole of what turns "can we hit 1 November?"
	 * into a question about hours that a forecast can already answer.
	 *
	 * <p>
	 * <strong>The two have to agree exactly at the boundary, and that is the
	 * test.</strong> A budget of this many hours finishes on precisely the day it was
	 * asked about, and one hundredth of an hour more finishes later —
	 * {@code WorkingCalendarTests} asserts the round trip in both directions, because two
	 * functions that disagreed about a boundary would put a plan on the wrong side of a
	 * date it had just met.
	 *
	 * <p>
	 * <strong>A target on a weekend counts to the Friday before it.</strong> Nobody works
	 * the weekend, so a plan asked to be done "by Sunday" has exactly the budget it has
	 * by Friday — and saying otherwise would hand it two days of work nobody is going to
	 * do. That is the mirror of a Saturday <em>start</em> beginning on the Monday.
	 *
	 * <p>
	 * A target before work begins is no budget at all rather than a negative one. Nothing
	 * can be finished before it is started, and {@link #finishOn} agrees: even no work at
	 * all finishes on the first working day.
	 * @param startsOn the day work begins, or the weekend before it
	 * @param by the day somebody wants it done by
	 * @param hoursPerDay what <em>one</em> person's working day holds
	 * @throws IllegalArgumentException if the working day is not positive or is longer
	 * than a day holds
	 */
	public static BigDecimal hoursBy(LocalDate startsOn, LocalDate by, BigDecimal hoursPerDay) {
		Objects.requireNonNull(startsOn, "A budget has to start somewhere");
		Objects.requireNonNull(by, "A budget has to end somewhere");
		requireWorkingDay(hoursPerDay);
		LocalDate first = nextWorkingDay(startsOn);
		LocalDate last = previousWorkingDay(by);
		if (last.isBefore(first)) {
			return BigDecimal.ZERO;
		}
		long days = workingDaysTo(last) - workingDaysTo(first) + 1;
		return hoursPerDay.multiply(BigDecimal.valueOf(days));
	}

	/**
	 * The first day on or after this one that anybody works.
	 */
	private static LocalDate nextWorkingDay(LocalDate day) {
		return switch (day.getDayOfWeek()) {
			case SATURDAY -> day.plusDays(2);
			case SUNDAY -> day.plusDays(1);
			default -> day;
		};
	}

	/** The last day on or before this one that anybody works. */
	private static LocalDate previousWorkingDay(LocalDate day) {
		return switch (day.getDayOfWeek()) {
			case SATURDAY -> day.minusDays(1);
			case SUNDAY -> day.minusDays(2);
			default -> day;
		};
	}

	/**
	 * How many working days have passed before this one, counted rather than walked.
	 *
	 * <p>
	 * Five per whole week plus the position within the current one, which makes the gap
	 * between two working days a subtraction however far apart they are — the same reason
	 * {@link #finishOn} counts weeks instead of stepping through them. The epoch day is
	 * shifted by three because day zero was a Thursday, and {@code floorDiv} rather than
	 * {@code /} because a date before 1970 divides the wrong way otherwise: an off-by-one
	 * there would be a whole working week, and only for plans nobody will ever type.
	 */
	private static long workingDaysTo(LocalDate workingDay) {
		long weeks = Math.floorDiv(workingDay.toEpochDay() + 3, 7);
		return weeks * WORKING_DAYS_IN_A_WEEK + workingDay.getDayOfWeek().getValue() - 1;
	}

	private static void requireWorkingDay(BigDecimal hoursPerDay) {
		Objects.requireNonNull(hoursPerDay, "A date needs to know what a working day holds");
		if (hoursPerDay.signum() <= 0) {
			throw new IllegalArgumentException(
					"A working day has to hold something, but held " + hoursPerDay + " hours");
		}
		if (hoursPerDay.compareTo(LONGEST_DAY) > 0) {
			throw new IllegalArgumentException("A day holds 24 hours at the very most, but was told " + hoursPerDay);
		}
	}

}
