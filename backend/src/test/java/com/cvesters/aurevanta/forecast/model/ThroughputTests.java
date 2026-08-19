package com.cvesters.aurevanta.forecast.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.forecast.model.ThroughputForecast.Delivered;

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

	/** What the engine uses, so the two forecasts are read at the same sampling error. */
	private static final int RUNS = 10_000;

	private static final long SEED = 20260818L;

	@Test
	void countsWhatWasFinishedInEachWeek() {
		Throughput history = Throughput.of(
				List.of(MONDAY, MONDAY.plusDays(3), MONDAY.plusWeeks(1), MONDAY.plusWeeks(1).plusDays(4)),
				MONDAY.plusWeeks(1).plusDays(6));

		assertThat(history.weekCount()).isEqualTo(2);
		assertThat(history.completed()).isEqualTo(4);
		assertThat(history.perWeek()).isEqualTo(2.0);
		assertThat(history.best()).isEqualTo(2);
		assertThat(history.worst()).isEqualTo(2);
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

		assertThat(history.weekCount()).isEqualTo(4);
		assertThat(history.completed()).isEqualTo(10);
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

		assertThat(history.weekCount()).isEqualTo(1);
		assertThat(history.completed()).isEqualTo(2);
	}

	@Test
	void theNextMondayIsTheNextWeek() {
		Throughput history = Throughput.of(List.of(MONDAY.plusDays(6), MONDAY.plusDays(7)), MONDAY.plusDays(7));

		assertThat(history.weekCount()).isEqualTo(2);
		assertThat(history.best()).isEqualTo(1);
		assertThat(history.worst()).isEqualTo(1);
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

		assertThat(history.weekCount()).isEqualTo(1);
		assertThat(history.completed()).isEqualTo(3);
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
		LocalDate asOf = MONDAY.plusWeeks(2);
		Throughput forwards = Throughput.of(List.of(MONDAY, asOf, MONDAY.plusWeeks(1)), asOf);
		Throughput backwards = Throughput.of(List.of(asOf, MONDAY.plusWeeks(1), MONDAY), asOf);

		// Compared through what they produce rather than through their counts, which is
		// the
		// stronger statement: two histories that project identically are the same
		// history.
		assertThat(backwards.from()).isEqualTo(forwards.from());
		assertThat(backwards.project(6, RUNS, SEED)).isEqualTo(forwards.project(6, RUNS, SEED));
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

	// Projecting from it -------------------------------------------------------

	/**
	 * <strong>The oracle, and it needs no sampling error to be right.</strong> A team
	 * that finished exactly five a week for twenty weeks resamples to five whichever week
	 * it draws, so forty items take exactly eight weeks in every one of ten thousand
	 * runs. Anybody can divide forty by five; nothing a fitted distribution could offer
	 * would be checkable this way, which is most of decision 4.
	 */
	@Test
	void aTeamThatNeverVariesAnswersExactlyAndWithNoSpread() {
		ThroughputForecast forecast = steady(5, 20).project(40, RUNS, SEED);

		assertThat(forecast.meanWeeks()).isEqualTo(8.0);
		assertThat(forecast.standardDeviationWeeks()).isZero();
		assertThat(forecast.p10Weeks()).isEqualTo(8);
		assertThat(forecast.p50Weeks()).isEqualTo(8);
		assertThat(forecast.p95Weeks()).isEqualTo(8);
		assertThat(forecast.unfinishedRuns()).isZero();
	}

	/**
	 * <strong>The whole reason weeks are resampled rather than averaged.</strong> Ten and
	 * nothing, alternating, is the same five a week as a steady five — and the plan it
	 * produces is not the same plan. A mean would report them identically.
	 */
	@Test
	void theSameAverageWithAWorseWeekIsAWiderAnswer() {
		Throughput steady = steady(5, 20);
		Throughput lumpy = alternating(10, 0, 20);

		assertThat(lumpy.perWeek()).isEqualTo(steady.perWeek());

		ThroughputForecast even = steady.project(40, RUNS, SEED);
		ThroughputForecast uneven = lumpy.project(40, RUNS, SEED);

		assertThat(uneven.standardDeviationWeeks()).isGreaterThan(even.standardDeviationWeeks());
		assertThat(uneven.p90Weeks()).isGreaterThan(even.p90Weeks());
	}

	@Test
	void twiceTheBacklogIsAboutTwiceTheWeeks() {
		Throughput history = alternating(7, 3, 20);

		int forty = history.project(40, RUNS, SEED).p50Weeks();
		int eighty = history.project(80, RUNS, SEED).p50Weeks();

		assertThat(eighty).isCloseTo(forty * 2, within(2));
	}

	/**
	 * <strong>Decision 5, written as a test so that nobody later "fixes" it.</strong> A
	 * bootstrap can draw nothing worse than the worst week in its window, so the slowest
	 * run possible is every week being that one. A history whose worst week is three can
	 * never need more than fourteen weeks for forty items, however unlucky — and a team
	 * that stops dead one week a quarter, whose window happens to hold no such week, gets
	 * exactly this answer with no warning from the arithmetic.
	 */
	@Test
	void itCannotDrawAWeekWorseThanTheWorstOneObserved() {
		Throughput history = alternating(7, 3, 20);

		ThroughputForecast forecast = history.project(40, RUNS, SEED);

		assertThat(history.worst()).isEqualTo(3);
		assertThat(forecast.p95Weeks()).isLessThanOrEqualTo(14);
	}

	/**
	 * The same question twice is the same answer, or a reader refreshing sees the plan
	 * move.
	 */
	@Test
	void theSameSeedGivesTheSameAnswer() {
		Throughput history = alternating(10, 0, 20);

		assertThat(history.project(40, RUNS, SEED)).isEqualTo(history.project(40, RUNS, SEED));
	}

	/** Its mirror, so neither passes because the sampler is doing nothing at all. */
	@Test
	void aDifferentSeedGivesADifferentAnswer() {
		Throughput history = alternating(10, 0, 20);

		assertThat(history.project(40, RUNS, SEED)).isNotEqualTo(history.project(40, RUNS, SEED + 1));
	}

	// The route it took, which is the burn-up's cone ---------------------------

	/**
	 * <strong>The oracle again, and the trajectory has to satisfy it too.</strong> Five a
	 * week with forty to go delivers exactly five, ten, fifteen … in every run, so the
	 * cone is not a cone at all: it is a line, with its two edges on top of each other,
	 * and it lands on the backlog in the eighth week and stays there.
	 */
	@Test
	void aTeamThatNeverVariesWalksStraightUpToItsBacklog() {
		List<Delivered> route = steady(5, 20).project(40, RUNS, SEED).trajectory();

		assertThat(route).hasSize(9);
		assertThat(route.get(0)).isEqualTo(new Delivered(0, 0, 0, 0));
		assertThat(route.get(4)).isEqualTo(new Delivered(4, 20, 20, 20));
		assertThat(route.get(8)).isEqualTo(new Delivered(8, 40, 40, 40));
	}

	/**
	 * <strong>Nothing at week zero, whatever the history.</strong> The first point of a
	 * cone is the question being asked rather than an answer to it, and a picture whose
	 * band opened before any time had passed would be claiming uncertainty about what has
	 * already happened.
	 */
	@Test
	void weekZeroDeliversNothing() {
		assertThat(alternating(10, 0, 20).project(40, RUNS, SEED).trajectory().get(0))
			.isEqualTo(new Delivered(0, 0, 0, 0));
	}

	/**
	 * <strong>The cone narrows, and it is worth knowing why.</strong> Not because the
	 * uncertainty falls away — a bootstrap draws the same weeks at the end as at the
	 * beginning — but because the backlog is a ceiling every run arrives at. The picture
	 * closes for the same reason a burn-up must never rise above its own total, and a
	 * reader taking confidence from the narrowing is reading the ceiling.
	 */
	@Test
	void theConeIsWidestInTheMiddleAndClosesOnTheBacklog() {
		List<Delivered> route = alternating(10, 0, 20).project(40, RUNS, SEED).trajectory();

		Delivered last = route.get(route.size() - 1);
		assertThat(width(last)).isZero();
		assertThat(last.p10()).isEqualTo(40);
		assertThat(width(route.get(4))).isGreaterThan(width(route.get(route.size() - 2)));
		assertThat(width(route.get(4))).isPositive();
	}

	/**
	 * And it never goes backwards, whichever edge is read: a burn-up counts finished
	 * work, and finished work does not become unfinished.
	 */
	@Test
	void everyEdgeOfTheConeOnlyEverClimbs() {
		List<Delivered> route = alternating(7, 3, 20).project(40, RUNS, SEED).trajectory();

		for (int week = 1; week < route.size(); week++) {
			Delivered was = route.get(week - 1);
			Delivered now = route.get(week);
			assertThat(now.p10()).as("low edge at week %d", week).isGreaterThanOrEqualTo(was.p10());
			assertThat(now.p50()).as("middle at week %d", week).isGreaterThanOrEqualTo(was.p50());
			assertThat(now.p90()).as("high edge at week %d", week).isGreaterThanOrEqualTo(was.p90());
		}
	}

	/**
	 * <strong>The cone and the date are one forecast read twice.</strong> The low edge
	 * reaches the backlog exactly when nine runs in ten have finished, which is
	 * {@code p90Weeks} — so a reader cannot find the picture saying one thing and the
	 * sentence beside it another. Its own week is where the drawing stops, at
	 * {@code p95Weeks}.
	 */
	@Test
	void theConeAndTheDateAreReadingsOfOneForecast() {
		ThroughputForecast forecast = alternating(7, 3, 20).project(40, RUNS, SEED);
		List<Delivered> route = forecast.trajectory();

		assertThat(route).hasSize(forecast.p95Weeks() + 1);
		assertThat(route.get(forecast.p90Weeks()).p10()).isEqualTo(40);
		assertThat(route.get(forecast.p90Weeks() - 1).p10()).isLessThan(40);
	}

	/**
	 * <strong>Watching where the runs went takes no draw of its own.</strong> The
	 * trajectory is accumulated from numbers the loop already produced, so every seeded
	 * answer this product gave before it existed is the answer it gives now — which is
	 * {@code RunObserver}'s property, asserted the same way: the figures beside it are
	 * byte for byte what they were. The seven numbers below were read off this class
	 * before the trajectory was written, which is the only way an assertion like this
	 * means anything at all.
	 */
	@Test
	void recordingTheRouteMovedNoAnswerAnybodyHadAlreadyBeenGiven() {
		ThroughputForecast forecast = alternating(7, 3, 20).project(40, RUNS, SEED);

		assertThat(forecast.meanWeeks()).isEqualTo(8.4348);
		assertThat(forecast.standardDeviationWeeks()).isEqualTo(1.2400600630615937);
		assertThat(forecast.p10Weeks()).isEqualTo(7);
		assertThat(forecast.p50Weeks()).isEqualTo(8);
		assertThat(forecast.p80Weeks()).isEqualTo(10);
		assertThat(forecast.p90Weeks()).isEqualTo(10);
		assertThat(forecast.p95Weeks()).isEqualTo(11);
	}

	// What it refuses, and what it will not sit in a loop for -------------------

	@Test
	void aBacklogOfNothingIsNotAForecastOfNoWeeks() {
		Throughput history = steady(5, 20);

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> history.project(0, RUNS, SEED));
		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> history.project(-1, RUNS, SEED));
	}

	@Test
	void refusesToMakeNoRuns() {
		Throughput history = steady(5, 20);

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> history.project(40, 0, SEED));
	}

	/**
	 * A team that has finished nothing never covers a backlog, so this answers rather
	 * than spins — the loop's termination cannot rest on the data being reasonable.
	 */
	@Test
	void aTeamThatHasFinishedNothingCannotProject() {
		Throughput nothing = Throughput.of(List.of(), MONDAY);

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> nothing.project(40, RUNS, SEED));
	}

	/**
	 * <strong>The same problem in slow motion, which a zero-history guard alone would
	 * miss.</strong> One completion in ten years covers a backlog of a hundred somewhere
	 * in the next millennium. Every run stops at the horizon and is counted, rather than
	 * the week count being quietly returned as though the plan finished there.
	 */
	@Test
	void aRateTooSlowToFinishStopsAtTheHorizonAndSaysSo() {
		Throughput barelyMoving = Throughput.of(List.of(MONDAY), MONDAY.plusWeeks(Throughput.MOST_WEEKS - 1L));

		ThroughputForecast forecast = barelyMoving.project(100, 200, SEED);

		assertThat(forecast.unfinishedRuns()).isEqualTo(200);
		assertThat(forecast.p50Weeks()).isEqualTo(Throughput.MOST_WEEKS);
	}

	/**
	 * One completion in the first week and one in the last, so the span is exactly as
	 * asked.
	 */
	private static Throughput weeksOfHistory(int weeks) {
		return Throughput.of(List.of(MONDAY), MONDAY.plusWeeks(weeks - 1L));
	}

	/** How far apart the two edges of the cone are in one week. */
	private static int width(Delivered week) {
		return week.p90() - week.p10();
	}

	/** A team that finished the same number every week. */
	private static Throughput steady(int each, int weeks) {
		return alternating(each, each, weeks);
	}

	/** A team whose weeks went one way and then the other, turn and turn about. */
	private static Throughput alternating(int odd, int even, int weeks) {
		List<LocalDate> completions = new ArrayList<>();
		for (int week = 0; week < weeks; week++) {
			LocalDate day = MONDAY.plusWeeks(week);
			for (int item = 0; item < ((week % 2 == 0) ? odd : even); item++) {
				completions.add(day);
			}
		}
		return Throughput.of(completions, MONDAY.plusWeeks(weeks - 1L));
	}

}
