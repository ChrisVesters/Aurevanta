package com.cvesters.aurevanta.forecast.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.forecast.model.Drift.Reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Whether a plan's date keeps moving out, or is merely moving.
 *
 * <p>
 * <strong>The measurement is the point of this class and these are it, made into
 * cases.</strong> `m10-plan.md` measured a rule about the direction of the last few runs
 * firing on 86% of plans re-forecast weekly for six months with no slide in them at all,
 * and no run length rescuing it. So the first case here is a plan that walks out four
 * times running and has not moved an inch since it started, and it must say nothing —
 * that is the 86% turned into a test rather than a paragraph, which is what this step's
 * own <em>done when</em> asks for.
 *
 * <p>
 * The rest of it is the yardstick: the same eight days are the whole of a plan that
 * claimed to know its finish to within four, and nothing at all on one that already said
 * a month either way.
 */
class DriftTests {

	private static final LocalDate MONDAY = LocalDate.parse("2026-08-17");

	/** Three working weeks, which is an ordinary band on a plan of this size. */
	private static final int THREE_WEEKS = 21;

	// The measurement -----------------------------------------------------------

	/**
	 * <strong>Four increases in a row and nothing to report.</strong> The plan dipped,
	 * and has been climbing back out of the dip ever since — which is what a plan that is
	 * not slipping looks like from close up, and what every rule about direction reads as
	 * a slide.
	 */
	@Test
	void aPlanThatEndsWhereItStartedIsNotSlidingHoweverItGotThere() {
		List<Reading> history = walk(THREE_WEEKS, 0, -8, -6, -4, -2, 0);

		Drift drift = Drift.since(history);

		assertThat(increasesAtTheEnd(history)).as("what a direction rule would have seen").isGreaterThanOrEqualTo(3);
		assertThat(drift.days()).isZero();
		assertThat(drift.movingOut()).isFalse();
		assertThat(drift.runs()).isEqualTo(6);
	}

	/**
	 * And a plan drifting out a day a week says so — once it has drifted past what its
	 * own band already admits to, and not one day before.
	 */
	@Test
	void aPlanDriftingADayAWeekFiresOnceItPassesTheBarAndNotBefore() {
		assertThat(Drift.since(steadily(THREE_WEEKS, 10)).movingOut()).isFalse();
		assertThat(Drift.since(steadily(THREE_WEEKS, 11)).movingOut()).isTrue();
	}

	/**
	 * <strong>The whole of decision 5.</strong> Eight days is the plan coming apart on
	 * one that said it knew its finish to within four, and inside the ordinary movement
	 * of one that said a month.
	 */
	@Test
	void theSameDriftIsASlideOnANarrowBandAndNothingOnAWideOne() {
		assertThat(Drift.since(steadily(4, 8)).movingOut()).isTrue();
		assertThat(Drift.since(steadily(30, 8)).movingOut()).isFalse();
	}

	/**
	 * <strong>A band of no days is a short plan and not a confident one, so it gets no
	 * verdict.</strong> Both ends of a band are rounded up to a whole day on their own,
	 * so a plan of a few days puts them on the same one — and that is exactly the plan
	 * where `m10-plan.md` step 3 measured re-running alone moving the date by
	 * <em>days</em>. The measurement that says this detector needs no noise floor was
	 * taken on a twelve-item chain and does not hold here, so the yardstick being missing
	 * means no answer rather than the strictest answer in the product.
	 */
	@Test
	void aPlanWhoseBandIsUnderADayHasNoYardstickAndSaysNothing() {
		assertThat(Drift.since(steadily(0, 1)).movingOut()).isFalse();
		assertThat(Drift.since(steadily(0, 40)).movingOut()).isFalse();
		// The distance is still reported; it is the verdict that is withheld.
		assertThat(Drift.since(steadily(0, 40)).days()).isEqualTo(40);
	}

	/**
	 * It answers one question. A plan that came in by a fortnight is a plan somebody
	 * should hear about from a different feature — putting it in this field would leave a
	 * flag nobody could read without also reading the sign of the number beside it.
	 */
	@Test
	void aPlanComingInIsNeverAPlanMovingOut() {
		Drift drift = Drift.since(steadily(4, -14));

		assertThat(drift.days()).isEqualTo(-14);
		assertThat(drift.movingOut()).isFalse();
	}

	// What ends a window --------------------------------------------------------

	/**
	 * <strong>Somebody halved the capacity, and the fortnight that moved is not a
	 * slide.</strong> Drift measured across that boundary is `roadmap.md`'s own warning —
	 * a comparison of two different questions reported as a plan that moved.
	 */
	@Test
	void anAssumptionThatChangedStartsANewWindow() {
		List<Reading> history = new ArrayList<>(weekly(THREE_WEEKS));
		history.set(2, asked(history.get(2), with(terms -> terms.capacity(1))));

		Drift drift = Drift.since(history);

		assertThat(drift.runs()).isEqualTo(2);
		assertThat(drift.fromDate()).isEqualTo(history.get(1).date());
	}

	/** And so does a working day, whose dates are not on one scale at all. */
	@Test
	void aWorkingDayThatChangedStartsANewWindow() {
		List<Reading> history = new ArrayList<>(weekly(THREE_WEEKS));
		history.set(3, asked(history.get(3), with(terms -> terms.workingHoursPerDay("8.00"))));

		assertThat(Drift.since(history).runs()).isEqualTo(3);
	}

	/**
	 * The one that is a refusal everywhere else in this milestone is only a boundary
	 * here: there is nothing to compare a run against across a version bump, so the
	 * window stops at it rather than the whole history being declined.
	 */
	@Test
	void anOlderEngineStartsANewWindow() {
		List<Reading> history = new ArrayList<>(weekly(THREE_WEEKS));
		history.set(1, asked(history.get(1), with(terms -> terms.engineVersion(1))));

		assertThat(Drift.since(history).runs()).isEqualTo(1);
	}

	/**
	 * <strong>A start that moved is not a different question, and this is the decision in
	 * this class most likely to be reversed by somebody reading decision 3 on its
	 * own.</strong> A plan re-forecast weekly is started from today every time, so
	 * counting that as a new question would end every window at one run and nothing would
	 * ever be reported. It is also the wrong reading: a finish date that held still while
	 * the start moved a week is a plan that delivered a week's work, and one that moved
	 * out with it is exactly what this exists to notice.
	 */
	@Test
	void aStartThatMovedDoesNotEndTheWindow() {
		List<Reading> history = new ArrayList<>(walk(THREE_WEEKS, 0, 3, 6, 9, 11));
		for (int index = 0; index < history.size(); index++) {
			LocalDate started = MONDAY.plusWeeks(history.size() - index);
			history.set(index, asked(history.get(index), with(terms -> terms.startsOn(started))));
		}

		Drift drift = Drift.since(history);

		assertThat(drift.runs()).isEqualTo(history.size());
		assertThat(drift.movingOut()).isTrue();
	}

	// The awkward histories -----------------------------------------------------

	/** A plan has not drifted from itself, which is an answer rather than a refusal. */
	@Test
	void oneForecastHasNotDriftedFromAnything() {
		Drift drift = Drift.since(List.of(landing(0, THREE_WEEKS)));

		assertThat(drift.runs()).isEqualTo(1);
		assertThat(drift.days()).isZero();
		assertThat(drift.fromDate()).isEqualTo(drift.toDate());
		assertThat(drift.movingOut()).isFalse();
	}

	/** Two is the first history that can say anything, and it says it. */
	@Test
	void twoForecastsAreEnoughToDrift() {
		Drift drift = Drift.since(steadily(THREE_WEEKS, 12));

		assertThat(drift.runs()).isEqualTo(2);
		assertThat(drift.days()).isEqualTo(12);
		assertThat(drift.movingOut()).isTrue();
	}

	/**
	 * A run made before M4 has hours and no date, so there is nothing here to measure in
	 * days — and reading it under today's calendar is what `V14` refused to do by not
	 * backfilling.
	 */
	@Test
	void aHistoryWithNoCalendarHasNoDaysAndNoVerdict() {
		Drift drift = Drift
			.since(List.of(new Reading(terms(), null, null, null), new Reading(terms(), null, null, null)));

		assertThat(drift.runs()).isEqualTo(2);
		assertThat(drift.days()).isNull();
		assertThat(drift.bandDays()).isNull();
		assertThat(drift.movingOut()).isFalse();
	}

	/**
	 * And half a band is no band, which is a guard rather than a scenario: the two ends
	 * are read through one calendar, so nothing this product writes has one of them. The
	 * yardstick is what the flag rests on, so its absence has to be the absence of the
	 * flag rather than a comparison against nothing — the same answer
	 * {@code halfACalendarIsNoCalendar} asks of a decomposition.
	 */
	@Test
	void aBandWithOnlyOneEndIsNothingToJudgeADriftAgainst() {
		Drift drift = Drift.since(List.of(new Reading(terms(), MONDAY.plusDays(40), MONDAY, null), landing(0, 4)));

		assertThat(drift.days()).isEqualTo(40);
		assertThat(drift.bandDays()).isNull();
		assertThat(drift.movingOut()).isFalse();
	}

	/**
	 * A plan nobody has forecast has no date to have moved, and answering that with a
	 * drift of nought would be a claim about a plan this has never seen.
	 */
	@Test
	void aPlanWithNoForecastsHasNothingToSay() {
		List<Reading> nothing = List.of();

		assertThatThrownBy(() -> Drift.since(nothing)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Drift.window(nothing)).isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * <strong>The window is a fact about the runs and not about any percentile</strong>,
	 * which is what lets one answer serve three confidences. Read on its own here,
	 * because a caller taking it off whichever reading it happened to compute last would
	 * be depending on that being true rather than asking for it.
	 */
	@Test
	void theWindowIsTheSameWhicheverPercentileIsRead() {
		List<Reading> history = new ArrayList<>(weekly(THREE_WEEKS));
		history.set(2, asked(history.get(2), with(terms -> terms.capacity(1))));

		assertThat(Drift.window(history)).isEqualTo(2);
		assertThat(Drift.window(history)).isEqualTo(Drift.since(history).runs());
	}

	// Fixtures ------------------------------------------------------------------

	/**
	 * A history from its dates, oldest first as anybody would write one down, handed back
	 * newest first as the API lists it.
	 */
	private static List<Reading> walk(int bandDays, int... daysOutOldestFirst) {
		List<Reading> readings = new ArrayList<>(daysOutOldestFirst.length);
		for (int index = daysOutOldestFirst.length - 1; index >= 0; index--) {
			readings.add(landing(daysOutOldestFirst[index], bandDays));
		}
		return readings;
	}

	/** Two forecasts, the newer one that many days further out than the older. */
	private static List<Reading> steadily(int bandDays, int daysOut) {
		return walk(bandDays, 0, daysOut);
	}

	/** Six forecasts a week apart, each a day further out than the last. */
	private static List<Reading> weekly(int bandDays) {
		return walk(bandDays, 0, 1, 2, 3, 4, 5);
	}

	private static Reading landing(int daysOut, int bandDays) {
		LocalDate date = MONDAY.plusDays(daysOut);
		return new Reading(terms(), date, date, date.plusDays(bandDays));
	}

	/** The same landing, asked on different terms. */
	private static Reading asked(Reading reading, ForecastTerms terms) {
		return new Reading(terms, reading.date(), reading.bandFrom(), reading.bandTo());
	}

	/**
	 * How many times in a row the date went out, counting back from the newest — which is
	 * what a rule about direction reads, and is here so that the case which refuses to
	 * fire says what it is refusing.
	 */
	private static int increasesAtTheEnd(List<Reading> newestFirst) {
		int found = 0;
		while (found + 1 < newestFirst.size()
				&& newestFirst.get(found).date().isAfter(newestFirst.get(found + 1).date())) {
			found++;
		}
		return found;
	}

	private static ForecastTerms terms() {
		return with((builder) -> builder);
	}

	private static ForecastTerms with(java.util.function.UnaryOperator<Builder> change) {
		return change.apply(new Builder()).build();
	}

	/**
	 * A run's terms, with one of them changed — so each case names only what it varies.
	 */
	private static final class Builder {

		private int engineVersion = 2;

		private String workingHoursPerDay = "6.00";

		private int capacity = 2;

		private LocalDate startsOn = MONDAY;

		Builder engineVersion(int version) {
			this.engineVersion = version;
			return this;
		}

		Builder workingHoursPerDay(String hours) {
			this.workingHoursPerDay = hours;
			return this;
		}

		Builder capacity(int capacity) {
			this.capacity = capacity;
			return this;
		}

		Builder startsOn(LocalDate day) {
			this.startsOn = day;
			return this;
		}

		ForecastTerms build() {
			return new ForecastTerms(this.engineVersion, "most_work_waiting", "five_day_week",
					new BigDecimal(this.workingHoursPerDay), this.capacity, new BigDecimal("30.00"),
					new BigDecimal("20.00"), new BigDecimal("60.00"), this.startsOn);
		}

	}

}
