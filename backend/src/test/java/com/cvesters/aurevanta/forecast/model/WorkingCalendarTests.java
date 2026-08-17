package com.cvesters.aurevanta.forecast.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * <strong>Two of these are worth more than the rest put together.</strong>
 * {@code twentyPointOhOneHoursAtSixPointSixSevenIsThreeDaysAndNotFour} is decision 7 as a
 * regression: a double gets that one wrong by a whole day, and every other arithmetic
 * case here passes either way. The one about capacity is decision 2, which is the mistake
 * this milestone exists to avoid — dividing by a team's daily total rather than one
 * worker's produces a date that is wrong by exactly the capacity factor, and every test
 * that only checks <em>a date came out</em> passes.
 */
class WorkingCalendarTests {

	private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);

	private static final LocalDate THURSDAY = LocalDate.of(2026, 8, 20);

	private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 15);

	private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 16);

	private static final BigDecimal SIX_HOUR_DAY = new BigDecimal("6.00");

	@Test
	void thirtyHoursAtSixADayFromAMondayFinishesThatFriday() {
		assertThat(WorkingCalendar.finishOn(MONDAY, new BigDecimal("30.00"), SIX_HOUR_DAY))
			.isEqualTo(LocalDate.of(2026, 8, 21));
	}

	/**
	 * Nothing left to do is still a day, and it is the day the work would have been done
	 * on — not a refusal and not an absent answer.
	 */
	@Test
	void aPlanWithNothingLeftFinishesOnTheDayItStarts() {
		assertThat(WorkingCalendar.finishOn(MONDAY, BigDecimal.ZERO, SIX_HOUR_DAY)).isEqualTo(MONDAY);
	}

	/**
	 * The step, at the only place it is easy to get off by one: a full day's work fits in
	 * the first day, and a hundredth of an hour more does not.
	 */
	@Test
	void exactlyOneDaysWorkFinishesOnTheFirstDayAndAHundredthOfAnHourMoreTakesTwo() {
		assertThat(WorkingCalendar.finishOn(MONDAY, new BigDecimal("6.00"), SIX_HOUR_DAY)).isEqualTo(MONDAY);
		assertThat(WorkingCalendar.finishOn(MONDAY, new BigDecimal("6.01"), SIX_HOUR_DAY))
			.isEqualTo(LocalDate.of(2026, 8, 18));
	}

	/**
	 * Nobody works the weekend, so a plan handed one gains nothing from it — it starts on
	 * the Monday and says so by finishing exactly where a Monday start finishes.
	 */
	@Test
	void aWeekendStartBehavesExactlyLikeTheFollowingMonday() {
		BigDecimal hours = new BigDecimal("30.00");
		LocalDate fromMonday = WorkingCalendar.finishOn(MONDAY, hours, SIX_HOUR_DAY);

		assertThat(WorkingCalendar.finishOn(SATURDAY, hours, SIX_HOUR_DAY)).isEqualTo(fromMonday);
		assertThat(WorkingCalendar.finishOn(SUNDAY, hours, SIX_HOUR_DAY)).isEqualTo(fromMonday);
		assertThat(WorkingCalendar.finishOn(SATURDAY, BigDecimal.ZERO, SIX_HOUR_DAY)).isEqualTo(MONDAY);
	}

	/**
	 * The weekend inside the remainder rather than inside a whole week: three days from a
	 * Thursday is Thursday, Friday and the Monday after, counted out on a calendar.
	 */
	@Test
	void aWeekEndedInTheMiddleOfTheRemainingDaysIsStillSkipped() {
		assertThat(WorkingCalendar.finishOn(THURSDAY, new BigDecimal("18.00"), SIX_HOUR_DAY))
			.isEqualTo(LocalDate.of(2026, 8, 24));
	}

	/**
	 * Fifty working days from a Monday, which a hand count puts on the Friday sixty-seven
	 * days later: ten weekends of two days each, and the four days after the last whole
	 * week.
	 */
	@Test
	void tenWeeksOfWorkLandsWhereAHandCountPutsIt() {
		assertThat(WorkingCalendar.finishOn(MONDAY, new BigDecimal("300.00"), SIX_HOUR_DAY))
			.isEqualTo(MONDAY.plusDays(67))
			.isEqualTo(LocalDate.of(2026, 10, 23));
	}

	/**
	 * <strong>Decision 7, as a regression with its own name.</strong> 20.01 divided by
	 * 6.67 is exactly 3 in decimal and 3.0000000000000004 in binary, and the ceiling
	 * turns that last bit into a whole extra day. 6.67 is not contrived: it is two thirds
	 * of a ten-hour day, or a twenty-hour week across three of them.
	 */
	@Test
	void twentyPointOhOneHoursAtSixPointSixSevenIsThreeDaysAndNotFour() {
		BigDecimal hours = new BigDecimal("20.01");
		BigDecimal hoursPerDay = new BigDecimal("6.67");

		assertThat(hours.doubleValue() / hoursPerDay.doubleValue()).isGreaterThan(3.0);
		assertThat(WorkingCalendar.finishOn(MONDAY, hours, hoursPerDay)).isEqualTo(LocalDate.of(2026, 8, 19));
	}

	@Test
	void moreHoursNeverFinishesEarlier() {
		LocalDate previous = MONDAY;

		for (BigDecimal hours = BigDecimal.ZERO; hours.compareTo(new BigDecimal("400")) <= 0; hours = hours
			.add(new BigDecimal("0.25"))) {
			LocalDate finish = WorkingCalendar.finishOn(MONDAY, hours, new BigDecimal("6.50"));
			assertThat(finish).isAfterOrEqualTo(previous);
			previous = finish;
		}
	}

	@Test
	void aLongerWorkingDayNeverFinishesLater() {
		LocalDate previous = LocalDate.MAX;

		for (BigDecimal hoursPerDay = new BigDecimal("0.25"); hoursPerDay
			.compareTo(new BigDecimal("24")) <= 0; hoursPerDay = hoursPerDay.add(new BigDecimal("0.25"))) {
			LocalDate finish = WorkingCalendar.finishOn(MONDAY, new BigDecimal("137.75"), hoursPerDay);
			assertThat(finish).isBeforeOrEqualTo(previous);
			previous = finish;
		}
	}

	/**
	 * <strong>Decision 2, from the only angle that can catch it.</strong> Eight six-hour
	 * items take 48 hours at capacity 1 and 12 at capacity 4, because the scheduler
	 * already ran four of them at a time. Converting divides by <em>one</em> worker's
	 * day, so the four-person plan takes two working days rather than the one that
	 * dividing by a team's daily total would produce — a date wrong by exactly the
	 * capacity factor, with nothing on screen looking amiss.
	 */
	@Test
	void convertingToDaysDoesNotUndoTheCapacityTheSchedulerAlreadyApplied() {
		double[] efforts = new double[8];
		Arrays.fill(efforts, 6.0);
		boolean[] untouched = new boolean[8];
		double alone = Schedule.of(List.of(), efforts, untouched, 1).finish(efforts);
		double together = Schedule.of(List.of(), efforts, untouched, 4).finish(efforts);

		assertThat(alone).isEqualTo(48.0);
		assertThat(together).isEqualTo(12.0);

		LocalDate byOne = WorkingCalendar.finishOn(MONDAY, BigDecimal.valueOf(alone), SIX_HOUR_DAY);
		LocalDate byFour = WorkingCalendar.finishOn(MONDAY, BigDecimal.valueOf(together), SIX_HOUR_DAY);

		// Eight working days, and two — not eight and a half of one.
		assertThat(byOne).isEqualTo(LocalDate.of(2026, 8, 26));
		assertThat(byFour).isEqualTo(LocalDate.of(2026, 8, 18));
		assertThat(byFour).isNotEqualTo(WorkingCalendar.finishOn(MONDAY, BigDecimal.valueOf(together),
				SIX_HOUR_DAY.multiply(BigDecimal.valueOf(4))));
	}

	@Test
	void refusesAWorkingDayThatHoldsNothingOrLessThanNothing() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> WorkingCalendar.finishOn(MONDAY, new BigDecimal("30.00"), BigDecimal.ZERO));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> WorkingCalendar.finishOn(MONDAY, new BigDecimal("30.00"), new BigDecimal("-6.00")));
	}

	/**
	 * Twenty-four is the most a day can hold and is allowed; anything past it is somebody
	 * describing a week, and the date it produces would be a lie in the honest direction.
	 */
	@Test
	void refusesAWorkingDayLongerThanADayHolds() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> WorkingCalendar.finishOn(MONDAY, new BigDecimal("30.00"), new BigDecimal("25.00")));
		assertThat(WorkingCalendar.finishOn(MONDAY, new BigDecimal("30.00"), new BigDecimal("24.00")))
			.isEqualTo(LocalDate.of(2026, 8, 18));
	}

	/**
	 * A negative amount of work is a bug upstream, and the ceiling would quietly read it
	 * as none — a date arriving in place of a failure.
	 */
	@Test
	void refusesLessWorkThanNone() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> WorkingCalendar.finishOn(MONDAY, new BigDecimal("-0.01"), SIX_HOUR_DAY));
	}

	@Test
	void refusesToInventAnythingItWasNotGiven() {
		assertThatNullPointerException()
			.isThrownBy(() -> WorkingCalendar.finishOn(null, new BigDecimal("30.00"), SIX_HOUR_DAY));
		assertThatNullPointerException().isThrownBy(() -> WorkingCalendar.finishOn(MONDAY, null, SIX_HOUR_DAY));
		assertThatNullPointerException()
			.isThrownBy(() -> WorkingCalendar.finishOn(MONDAY, new BigDecimal("30.00"), null));
	}

	@Test
	void theRuleItAppliesHasAName() {
		assertThat(WorkingCalendar.RULE).isEqualTo("five_day_week");
	}

}
