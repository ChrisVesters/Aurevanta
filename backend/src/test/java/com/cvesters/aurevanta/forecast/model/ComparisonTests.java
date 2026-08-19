package com.cvesters.aurevanta.forecast.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.forecast.model.Comparison.Difference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What two forecasts of one plan share, and what they do not.
 *
 * <p>
 * <strong>Each difference is asserted on its own, and that is deliberate rather than
 * thorough.</strong> One case changing several fields and checking "they differ" would
 * pass whichever of them the code actually noticed — and the failure this class exists to
 * prevent is a date that moved because somebody adjusted the working day being reported
 * as a plan slipping, which is a report about the one field nobody checked.
 */
class ComparisonTests {

	private static final LocalDate MONDAY = LocalDate.parse("2026-08-17");

	@Test
	void twoRunsOnTheSameTermsDisagreeAboutNothing() {
		Comparison comparison = Comparison.between(terms(), terms());

		assertThat(comparison.comparable()).isTrue();
		assertThat(comparison.sameCalendar()).isTrue();
		assertThat(comparison.sameAssumptions()).isTrue();
		assertThat(comparison.sameStart()).isTrue();
		assertThat(comparison.differences()).isEmpty();
	}

	// Each of them, alone -------------------------------------------------------

	/**
	 * <strong>The one that refuses rather than reports.</strong> A ranking from a
	 * different model is an exact ranking of a plan nobody forecast — the contribution
	 * ranking's argument, and the only difference here that means there is nothing to
	 * compare with rather than something to say.
	 */
	@Test
	void aDifferentEngineIsNothingToCompareWith() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.engineVersion(3)));

		assertThat(comparison.comparable()).isFalse();
		assertThat(comparison.differences()).containsExactly(Difference.ENGINE_VERSION);
		// And it says nothing about the rest, which are all still the same.
		assertThat(comparison.sameCalendar()).isTrue();
		assertThat(comparison.sameAssumptions()).isTrue();
	}

	/**
	 * <strong>The second of the two that refuse, and the one nothing can produce
	 * yet.</strong> A priority rule is a stored name like a calendar rule and belongs
	 * with the engine version rather than with it: a calendar is laid over an answer the
	 * engine has already given, so two calendars are one answer read twice — a priority
	 * rule is inside the scheduler, so two rules are two answers. There is one rule
	 * today, which is exactly why this is asserted now: the day a second ships, every
	 * plan re-forecast across the change would otherwise report the jump as drift.
	 */
	@Test
	void aDifferentPriorityRuleIsAlsoNothingToCompareWith() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.priorityRule("shortest_first")));

		assertThat(comparison.comparable()).isFalse();
		assertThat(comparison.differences()).containsExactly(Difference.PRIORITY_RULE);
		assertThat(comparison.sameCalendar()).isTrue();
		assertThat(comparison.sameAssumptions()).isTrue();
	}

	@Test
	void aDifferentCalendarRuleIsReportedAndDoesNotRefuse() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.calendarRule("four_day_week")));

		assertThat(comparison.comparable()).isTrue();
		assertThat(comparison.sameCalendar()).isFalse();
		assertThat(comparison.differences()).containsExactly(Difference.CALENDAR_RULE);
	}

	@Test
	void aDifferentWorkingDayIsADifferentDivisorAndNotAPlanThatMoved() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.workingHoursPerDay("8.00")));

		assertThat(comparison.sameCalendar()).isFalse();
		assertThat(comparison.differences()).containsExactly(Difference.WORKING_DAY);
	}

	@Test
	void aDifferentCapacityIsAnAssumptionAndNotACalendar() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.capacity(1)));

		assertThat(comparison.sameAssumptions()).isFalse();
		assertThat(comparison.sameCalendar()).isTrue();
		assertThat(comparison.differences()).containsExactly(Difference.CAPACITY);
	}

	/**
	 * <strong>A team of the same size and a different shape is a different
	 * question.</strong> Capacity sees six units either way; three and three becoming two
	 * and four moves the date, and a comparison blind to it would report that as a plan
	 * sliding — which is the one thing the drift detector exists to avoid.
	 */
	@Test
	void aReshapedTeamIsAnAssumptionEvenAtTheSameCapacity() {
		Comparison comparison = Comparison.between(with(older -> older.resourcing("[{\"units\":3},{\"units\":3}]")),
				with(newer -> newer.resourcing("[{\"units\":2},{\"units\":4}]")));

		assertThat(comparison.comparable()).isTrue();
		assertThat(comparison.sameAssumptions()).isFalse();
		assertThat(comparison.differences()).containsExactly(Difference.RESOURCES);
	}

	@Test
	void aDifferentBadStretchIsAnAssumption() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.teamFactor("40.00")));

		assertThat(comparison.sameAssumptions()).isFalse();
		assertThat(comparison.differences()).containsExactly(Difference.TEAM_FACTOR);
	}

	/**
	 * Either end of the range, and one difference rather than two: it is one question.
	 */
	@Test
	void eitherEndOfTheGrowthRangeIsTheSameDifference() {
		assertThat(Comparison.between(terms(), with(newer -> newer.growth("30.00", "60.00"))).differences())
			.containsExactly(Difference.SCOPE_GROWTH);
		assertThat(Comparison.between(terms(), with(newer -> newer.growth("20.00", "80.00"))).differences())
			.containsExactly(Difference.SCOPE_GROWTH);
	}

	/**
	 * Its own question rather than one of the assumptions, because it is the one nobody
	 * decided: a plan started a month later finishes a month later.
	 */
	@Test
	void aDifferentStartIsTimePassingRatherThanAJudgement() {
		Comparison comparison = Comparison.between(terms(), with(newer -> newer.startsOn(MONDAY.plusWeeks(4))));

		assertThat(comparison.sameStart()).isFalse();
		assertThat(comparison.sameAssumptions()).isTrue();
		assertThat(comparison.differences()).containsExactly(Difference.STARTS_ON);
	}

	// The awkward values --------------------------------------------------------

	/**
	 * <strong>{@code 30} and {@code 30.00} are the same assumption and are not equal
	 * objects.</strong> Both of these arrive from {@code numeric} columns and so agree on
	 * scale in practice, which is what makes this the kind of bug that survives every
	 * test until one value comes from somewhere else.
	 */
	@Test
	void theSameNumberWrittenTwoWaysIsTheSameAssumption() {
		ForecastTerms scaled = new ForecastTerms(2, "most_work_waiting", "five_day_week", new BigDecimal("6.00"), 2,
				"[]", new BigDecimal("30.00"), new BigDecimal("20.00"), new BigDecimal("60.00"), MONDAY);
		ForecastTerms plain = new ForecastTerms(2, "most_work_waiting", "five_day_week", new BigDecimal("6"), 2, "[]",
				new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("60"), MONDAY);

		assertThat(Comparison.between(scaled, plain).differences()).isEmpty();
	}

	/**
	 * A run made before the calendar has no calendar at all rather than a default one, so
	 * two of them agree with each other — and neither agrees with a run that has one.
	 */
	@Test
	void twoRunsMadeBeforeThereWasACalendarAgreeWithEachOther() {
		ForecastTerms before = new ForecastTerms(2, "most_work_waiting", null, null, 2, "[]", new BigDecimal("30.00"),
				new BigDecimal("20.00"), new BigDecimal("60.00"), null);

		assertThat(Comparison.between(before, before).differences()).isEmpty();
		assertThat(Comparison.between(before, terms()).differences())
			.containsExactlyInAnyOrder(Difference.CALENDAR_RULE, Difference.WORKING_DAY, Difference.STARTS_ON);
	}

	/**
	 * Which of the two is the older one changes nothing about what they disagree about —
	 * and nothing here should depend on the order a caller passes them in, since a screen
	 * walking a history backwards and one walking it forwards must say the same thing.
	 */
	@Test
	void whichOfThemIsOlderDoesNotChangeWhatTheyDisagreeAbout() {
		ForecastTerms before = new ForecastTerms(2, "most_work_waiting", null, null, 2, "[]", new BigDecimal("30.00"),
				new BigDecimal("20.00"), new BigDecimal("60.00"), null);
		ForecastTerms roomier = with((newer) -> newer.capacity(4));

		assertThat(Comparison.between(terms(), before)).isEqualTo(Comparison.between(before, terms()));
		assertThat(Comparison.between(roomier, terms())).isEqualTo(Comparison.between(terms(), roomier));
	}

	/** Several at once, because a real pair usually differs in more than one way. */
	@Test
	void severalDifferencesAreAllReported() {
		Comparison comparison = Comparison.between(terms(),
				with(newer -> newer.capacity(4).workingHoursPerDay("8.00")));

		assertThat(comparison.differences()).containsExactlyInAnyOrder(Difference.CAPACITY, Difference.WORKING_DAY);
		assertThat(comparison.comparable()).isTrue();
		assertThat(comparison.sameCalendar()).isFalse();
		assertThat(comparison.sameAssumptions()).isFalse();
	}

	// Fixtures ------------------------------------------------------------------

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

		private String priorityRule = "most_work_waiting";

		private String calendarRule = "five_day_week";

		private String workingHoursPerDay = "6.00";

		private int capacity = 2;

		private String resourcing = "[]";

		private String teamFactor = "30.00";

		private String growthLow = "20.00";

		private String growthHigh = "60.00";

		private LocalDate startsOn = MONDAY;

		Builder engineVersion(int version) {
			this.engineVersion = version;
			return this;
		}

		Builder priorityRule(String rule) {
			this.priorityRule = rule;
			return this;
		}

		Builder calendarRule(String rule) {
			this.calendarRule = rule;
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

		Builder resourcing(String declared) {
			this.resourcing = declared;
			return this;
		}

		Builder teamFactor(String percent) {
			this.teamFactor = percent;
			return this;
		}

		Builder growth(String low, String high) {
			this.growthLow = low;
			this.growthHigh = high;
			return this;
		}

		Builder startsOn(LocalDate day) {
			this.startsOn = day;
			return this;
		}

		ForecastTerms build() {
			return new ForecastTerms(this.engineVersion, this.priorityRule, this.calendarRule,
					new BigDecimal(this.workingHoursPerDay), this.capacity, this.resourcing,
					new BigDecimal(this.teamFactor), new BigDecimal(this.growthLow), new BigDecimal(this.growthHigh),
					this.startsOn);
		}

	}

}
