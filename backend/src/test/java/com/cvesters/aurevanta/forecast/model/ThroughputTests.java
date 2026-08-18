package com.cvesters.aurevanta.forecast.model;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

/**
 * How much a team finished, week by week.
 *
 * <p>
 * <strong>The case worth reading first is
 * {@link #aQuietFortnightIsPartOfTheHistory}.</strong> Completion dates arrive as a list
 * and the obvious thing to do with a list of dates is group them — which silently
 * produces only the weeks that had something in them, and inflates the rate by exactly
 * the fraction of the time the team was not delivering. Ten items in one week and nothing
 * for three is two and a half a week, and every other case here is a variation on making
 * sure the zeros survive.
 */
class ThroughputTests {

	/** A Monday, so that a week boundary is somewhere obvious. */
	private static final LocalDate MONDAY = LocalDate.parse("2026-03-02");

	@Test
	void countsWhatWasFinishedInEachWeek() {
		Throughput history = Throughput.of(
				List.of(MONDAY, MONDAY.plusDays(3), MONDAY.plusWeeks(1), MONDAY.plusWeeks(1).plusDays(4)),
				MONDAY.plusWeeks(1).plusDays(6));

		assertThat(history.weeks()).containsExactly(2, 2);
		assertThat(history.completed()).isEqualTo(4);
		assertThat(history.perWeek()).isEqualTo(2.0);
	}

	/**
	 * <strong>The decision this class exists for.</strong> Grouping the dates would
	 * answer ten a week; counting the weeks answers two and a half, which is what the
	 * team actually delivered.
	 */
	@Test
	void aQuietFortnightIsPartOfTheHistory() {
		List<LocalDate> tenInOneWeek = List.of(MONDAY, MONDAY, MONDAY, MONDAY, MONDAY, MONDAY.plusDays(1),
				MONDAY.plusDays(1), MONDAY.plusDays(2), MONDAY.plusDays(3), MONDAY.plusDays(4));

		Throughput history = Throughput.of(tenInOneWeek, MONDAY.plusWeeks(3));

		assertThat(history.weeks()).containsExactly(10, 0, 0, 0);
		assertThat(history.perWeek()).isEqualTo(2.5);
		assertThat(history.worst()).isZero();
		assertThat(history.best()).isEqualTo(10);
	}

	/**
	 * The last week is the week the question was asked in, not the week the last item
	 * landed in — otherwise a plan that has gone quiet reads exactly like one that is
	 * still moving.
	 */
	@Test
	void askingLaterAboutTheSameWorkReadsAsASlowerTeam() {
		List<LocalDate> four = List.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusWeeks(1), MONDAY.plusWeeks(1));

		Throughput promptly = Throughput.of(four, MONDAY.plusWeeks(1).plusDays(6));
		Throughput aMonthLater = Throughput.of(four, MONDAY.plusWeeks(5));

		assertThat(promptly.perWeek()).isEqualTo(2.0);
		assertThat(aMonthLater.perWeek()).isCloseTo(4.0 / 6.0, within(1e-12));
		assertThat(aMonthLater.weekCount()).isEqualTo(6);
	}

	/**
	 * The history starts when work did, not when the plan was written down. The idle
	 * months before anybody began would otherwise make the rate a property of when
	 * somebody opened a form — a bias in the optimistic direction, taken deliberately.
	 */
	@Test
	void theHistoryBeginsAtTheFirstCompletion() {
		Throughput history = Throughput.of(List.of(MONDAY.plusWeeks(10)), MONDAY.plusWeeks(11));

		assertThat(history.from()).isEqualTo(MONDAY.plusWeeks(10));
		assertThat(history.weekCount()).isEqualTo(2);
	}

	@Test
	void oneCompletionIsOneWeekAndNoSpread() {
		Throughput history = Throughput.of(List.of(MONDAY.plusDays(2)), MONDAY.plusDays(4));

		assertThat(history.weekCount()).isEqualTo(1);
		assertThat(history.best()).isEqualTo(history.worst());
		assertThat(history.perWeek()).isEqualTo(1.0);
		assertThat(history.from()).isEqualTo(MONDAY);
		assertThat(history.to()).isEqualTo(MONDAY);
	}

	// Where a week begins ------------------------------------------------------

	@Test
	void aWeekBeginsOnItsMondayAndEndsOnTheSunday() {
		Throughput history = Throughput.of(List.of(MONDAY, MONDAY.plusDays(6)), MONDAY.plusDays(6));

		assertThat(history.weeks()).containsExactly(2);
	}

	@Test
	void theNextMondayIsTheNextWeek() {
		Throughput history = Throughput.of(List.of(MONDAY.plusDays(6), MONDAY.plusDays(7)), MONDAY.plusDays(7));

		assertThat(history.weeks()).containsExactly(1, 1);
	}

	/**
	 * <strong>The turn of the year, which is where a week number would have gone
	 * wrong.</strong> 1 January 2026 is a Thursday, and the week it belongs to begins on
	 * 29 December 2025 — a Monday in the previous year. Keying a bucket by that Monday
	 * makes this unremarkable; ISO week numbers, which need a year beside them and
	 * disagree with the calendar for a few days each January, would have made it a case
	 * to get right.
	 */
	@Test
	void aWeekThatStraddlesTheNewYearIsOneWeek() {
		Throughput history = Throughput.of(
				List.of(LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-04")),
				LocalDate.parse("2026-01-04"));

		assertThat(history.weeks()).containsExactly(3);
		assertThat(history.from()).isEqualTo(LocalDate.parse("2025-12-29"));
		assertThat(history.to()).isEqualTo(LocalDate.parse("2025-12-29"));
	}

	// What it refuses and what it allows ---------------------------------------

	/**
	 * A history of no weeks rather than an error: a plan nobody has finished anything in.
	 */
	@Test
	void aTeamThatHasFinishedNothingHasNoHistoryRatherThanNoRate() {
		Throughput history = Throughput.of(List.of(), MONDAY);

		assertThat(history.observed()).isFalse();
		assertThat(history.weekCount()).isZero();
		assertThat(history.completed()).isZero();
		assertThat(history.weeks()).isEmpty();
		assertThat(history.worthShowing()).isFalse();
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(history::perWeek);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(history::best);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(history::worst);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(history::from);
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(history::to);
	}

	@Test
	void refusesWorkFinishedAfterTheDayBeingAskedAbout() {
		List<LocalDate> tomorrow = List.of(MONDAY.plusDays(1));

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> Throughput.of(tomorrow, MONDAY));
	}

	/**
	 * A caller that sorts and one that does not get the same history, because the
	 * earliest is found rather than taken off the front.
	 */
	@Test
	void theOrderTheCompletionsArriveInDoesNotMatter() {
		List<LocalDate> forwards = List.of(MONDAY, MONDAY.plusWeeks(2), MONDAY.plusWeeks(1));
		List<LocalDate> backwards = List.of(MONDAY.plusWeeks(2), MONDAY.plusWeeks(1), MONDAY);

		assertThat(Throughput.of(backwards, MONDAY.plusWeeks(2)).weeks())
			.containsExactly(Throughput.of(forwards, MONDAY.plusWeeks(2)).weeks());
	}

	/** A history that handed out the array it is made of would not be a value. */
	@Test
	void theWeeksCannotBeEditedFromOutside() {
		Throughput history = Throughput.of(List.of(MONDAY), MONDAY);

		history.weeks()[0] = 99;

		assertThat(history.weeks()).containsExactly(1);
		assertThat(history.completed()).isEqualTo(1);
	}

	// How little is too little -------------------------------------------------

	/**
	 * The two bars, asserted at their edges so that moving one is a deliberate act. A
	 * quarter is where an answer stops being random; a year is where it stops needing a
	 * warning beside it.
	 */
	@Test
	void aQuarterIsWorthShowingAndAYearIsWorthTrusting() {
		assertThat(weeksOfHistory(Throughput.WORTH_SHOWING - 1).worthShowing()).isFalse();
		assertThat(weeksOfHistory(Throughput.WORTH_SHOWING).worthShowing()).isTrue();
		assertThat(weeksOfHistory(Throughput.WORTH_TRUSTING - 1).worthTrusting()).isFalse();
		assertThat(weeksOfHistory(Throughput.WORTH_TRUSTING).worthTrusting()).isTrue();
	}

	/**
	 * Long enough to project from is not the same as long enough to project from
	 * unremarked.
	 */
	@Test
	void aQuarterOfHistoryIsShownAndFlagged() {
		Throughput quarter = weeksOfHistory(Throughput.WORTH_SHOWING);

		assertThat(quarter.worthShowing()).isTrue();
		assertThat(quarter.worthTrusting()).isFalse();
	}

	/**
	 * One completion in the first week and one in the last, so the span is exactly as
	 * asked.
	 */
	private static Throughput weeksOfHistory(int weeks) {
		return Throughput.of(List.of(MONDAY), MONDAY.plusWeeks(weeks - 1L));
	}

}
