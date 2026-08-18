package com.cvesters.aurevanta.forecast.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * How much a team finished, week by week — including the weeks it finished nothing.
 *
 * <p>
 * <strong>Items and not hours, which is what lets this answer on the day it
 * ships.</strong> A completion date is required on everything ever marked done, so the
 * history below exists in full for every plan this product holds.
 * {@code actual_effort_hours} carries no such requirement and M8's coverage counts are
 * the measurement of what that costs — a throughput built on hours would be a second
 * feature whose ordinary answer is "nothing to report yet".
 *
 * <p>
 * <strong>Every week from the first completion to the as-of date is in here, and the
 * empty ones are the point.</strong> Completion dates arrive as a list, the obvious thing
 * to do with a list of dates is group them, and grouping yields only the weeks that had
 * something in them — which inflates the rate by exactly the fraction of the time the
 * team was not delivering. Holidays, the incident, the week everybody was in a workshop:
 * those are what `roadmap.md` means when it says throughput "implicitly absorbs
 * interruptions, holidays … and the fact that nobody works eight focused hours", and
 * absorbing them means <em>counting</em> them. Ten items in one week and nothing for
 * three is two and a half a week, not ten.
 *
 * <p>
 * <strong>The history begins at the first completion and not before it.</strong> A plan
 * typed in last year and started last month is not evidence that this team does nothing,
 * so the idle months before anybody began are left out — otherwise the rate would be a
 * property of when somebody opened a form. That is a bias in the optimistic direction and
 * it is named rather than hidden: what this measures is the pace since work started, not
 * the pace since the plan was written down.
 */
public final class Throughput {

	/**
	 * What the week below is called, published with every answer read under it.
	 *
	 * <p>
	 * A name rather than a constant nobody can see, for the reason
	 * {@link WorkingCalendar#RULE} and {@link Schedule#PRIORITY_RULE} are names: two
	 * defensible definitions give two different histories from identical data, and on a
	 * short history one bucket boundary can move a percentile. A week beginning Sunday is
	 * a <em>second name</em> rather than an edit to this one.
	 */
	public static final String RULE = "monday_week";

	/** Where a week is taken to begin, stated once and read only by {@link #weekOf}. */
	private static final DayOfWeek WEEK_BEGINS = DayOfWeek.MONDAY;

	/**
	 * The fewest weeks worth projecting from — a quarter.
	 *
	 * <p>
	 * <strong>Chosen because refusing more would leave most teams with nothing, not
	 * because a quarter is comfortable.</strong> Simulated against a team completing five
	 * items a week with forty left, the 85% answer lands between seven and thirteen weeks
	 * on one month of history and between eight and twelve on a quarter, against a truth
	 * of ten. The first is not a wide forecast but a random one; the second is wide and
	 * centred, which is worth showing. `m9-plan.md` carries the tables.
	 */
	public static final int WORTH_SHOWING = 13;

	/**
	 * The fewest weeks that need no warning beside them — a year.
	 *
	 * <p>
	 * The same simulation with a team that loses one week in ten to an incident: at a
	 * quarter of history <strong>23% of teams have never observed their own bad
	 * week</strong>, and theirs is the forecast that comes back early and confident. At
	 * half a year it is 5%, and at a year none. Between {@link #WORTH_SHOWING} and here
	 * the answer is published and flagged, because a reader who knows their team is the
	 * only one who can tell whether the window in {@link #worst()} contains the week they
	 * are worried about.
	 */
	public static final int WORTH_TRUSTING = 52;

	private final int[] weeks;

	private final LocalDate from;

	private Throughput(int[] weeks, LocalDate from) {
		this.weeks = weeks;
		this.from = from;
	}

	/**
	 * A history from the days work was finished on, up to the day somebody is asking.
	 *
	 * <p>
	 * The order of the completions is not assumed — the earliest is found rather than
	 * taken from the front, so a caller that sorts and a caller that does not get the
	 * same history.
	 * @param completions the day each finished item was reported finished, one entry per
	 * item; empty when nothing has been finished, which is a history of no weeks rather
	 * than an error
	 * @param asOf the day the question is being asked, stated by the caller because what
	 * day it is where somebody is sitting is not something a server knows
	 * @throws IllegalArgumentException if anything was finished after the day being asked
	 * about, which is a fact about the input rather than a team that works fast
	 */
	public static Throughput of(List<LocalDate> completions, LocalDate asOf) {
		if (completions.isEmpty()) {
			return new Throughput(new int[0], null);
		}
		LocalDate earliest = asOf;
		for (LocalDate completion : completions) {
			if (completion.isAfter(asOf)) {
				throw new IllegalArgumentException("Work cannot be finished on " + completion + ", after " + asOf);
			}
			if (completion.isBefore(earliest)) {
				earliest = completion;
			}
		}
		LocalDate from = weekOf(earliest);
		int[] weeks = new int[(int) ChronoUnit.WEEKS.between(from, weekOf(asOf)) + 1];
		for (LocalDate completion : completions) {
			weeks[(int) ChronoUnit.WEEKS.between(from, weekOf(completion))]++;
		}
		return new Throughput(weeks, from);
	}

	/** Whether this team has been seen finishing anything at all. */
	public boolean observed() {
		return this.weeks.length > 0;
	}

	/**
	 * How many items were finished in each week, oldest first — the thing a projection
	 * resamples.
	 *
	 * <p>
	 * A copy, because a history that hands out the array it is made of is not a value. It
	 * is taken once by whatever is sampling from it rather than per run.
	 */
	public int[] weeks() {
		return this.weeks.clone();
	}

	/** How many weeks the history covers, empty ones included. */
	public int weekCount() {
		return this.weeks.length;
	}

	/** How much was finished across all of them. */
	public int completed() {
		int total = 0;
		for (int week : this.weeks) {
			total += week;
		}
		return total;
	}

	/**
	 * The average week, which is the number a reader will quote and the one a projection
	 * does <em>not</em> use — a mean says nothing about how a bad fortnight is
	 * distributed, which is the whole reason the weeks are resampled rather than
	 * averaged.
	 * @throws IllegalStateException if nothing has been finished
	 */
	public double perWeek() {
		requireObserved();
		return (double) completed() / this.weeks.length;
	}

	/**
	 * The best week observed.
	 * @throws IllegalStateException if nothing has been finished
	 */
	public int best() {
		requireObserved();
		int best = this.weeks[0];
		for (int week : this.weeks) {
			best = Math.max(best, week);
		}
		return best;
	}

	/**
	 * The worst week observed, which is the most useful number here and the one to put on
	 * screen.
	 *
	 * <p>
	 * A projection resampling this history can never produce a week worse than this one,
	 * so a reader who knows their team stops for a week each quarter can tell in one
	 * glance whether the window contains such a week. It is reported rather than
	 * corrected for: inventing a tail nobody observed is the alternative, and it would be
	 * a number with no source inside a forecast whose entire claim is that it came from
	 * the team.
	 * @throws IllegalStateException if nothing has been finished
	 */
	public int worst() {
		requireObserved();
		int worst = this.weeks[0];
		for (int week : this.weeks) {
			worst = Math.min(worst, week);
		}
		return worst;
	}

	/**
	 * The first week of the history, named by the day it begins on.
	 * @throws IllegalStateException if nothing has been finished
	 */
	public LocalDate from() {
		requireObserved();
		return this.from;
	}

	/**
	 * The last week of the history, which is the week the question was asked in rather
	 * than the week the last item landed in — a plan that has gone quiet has to read as
	 * quiet.
	 * @throws IllegalStateException if nothing has been finished
	 */
	public LocalDate to() {
		requireObserved();
		return this.from.plusWeeks(this.weeks.length - 1L);
	}

	/**
	 * Whether there is enough here to project from at all — see {@link #WORTH_SHOWING}.
	 */
	public boolean worthShowing() {
		return this.weeks.length >= WORTH_SHOWING;
	}

	/**
	 * Whether there is enough to project from unremarked — see {@link #WORTH_TRUSTING}.
	 */
	public boolean worthTrusting() {
		return this.weeks.length >= WORTH_TRUSTING;
	}

	/**
	 * The week a day belongs to, named by the day that week begins on.
	 *
	 * <p>
	 * The Monday on or before, and deliberately not an ISO week <em>number</em>: a number
	 * needs a year beside it, and the pair disagree with the calendar for a few days
	 * every January — 1 January 2026 is a Thursday in ISO week 1 of 2026, and the Monday
	 * its week begins on is 29 December 2025. Keying by that Monday is the same bucketing
	 * with none of the arithmetic that gets the turn of the year wrong.
	 */
	private static LocalDate weekOf(LocalDate day) {
		return day.with(TemporalAdjusters.previousOrSame(WEEK_BEGINS));
	}

	private void requireObserved() {
		if (!observed()) {
			throw new IllegalStateException("Nothing has been finished, so there is no history to read");
		}
	}

}
