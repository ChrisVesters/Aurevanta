package com.cvesters.aurevanta.forecast.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import com.cvesters.aurevanta.forecast.model.ThroughputForecast.Delivered;

/**
 * How much a team finished, week by week — including the weeks it finished nothing.
 *
 * <p>
 * <strong>Items and not hours, which is what lets this answer on the day it
 * ships.</strong> A completion date is required on everything ever marked done, so the
 * history below exists in full for every plan this product holds.
 * {@code actual_effort_hours} carries no such requirement and calibration's coverage
 * counts are the measurement of what that costs — a throughput built on hours would be a
 * second feature whose ordinary answer is "nothing to report yet".
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
	 * centred, which is worth showing. `docs/design/throughput.md` carries the tables.
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

	/**
	 * The furthest ahead this will look — ten years.
	 *
	 * <p>
	 * <strong>A bound rather than a budget.</strong> A bootstrap draws until the backlog
	 * is covered, and a history with one completion in five years covers a backlog of
	 * five hundred somewhere around the year 2500 — so without a stop the loop is
	 * unbounded in the one direction nobody tests. It is set where no answer would be
	 * worth reading anyway, and a run that reaches it is <em>counted</em> rather than
	 * quietly returned as a number: see {@link ThroughputForecast#unfinishedRuns()}.
	 */
	public static final int MOST_WEEKS = 520;

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
	 * How many weeks the history covers, empty ones included.
	 */
	public int weekCount() {
		return this.weeks.length;
	}

	/**
	 * What had been delivered by the end of each week, running total, oldest first.
	 *
	 * <p>
	 * <strong>The past half of a burn-up, and the only reason the weeks are published at
	 * all.</strong> They were deliberately not, until something needed to draw them: a
	 * copy handed to nobody is a copy nobody can be wrong about. A picture of what a team
	 * delivered is that reader, and it wants the running total rather than the weekly
	 * counts — which is also what stops this becoming a second way to compute
	 * {@link #perWeek}, since a cumulative series cannot be averaged into a rate without
	 * differencing it first.
	 */
	public int[] deliveredByWeek() {
		int[] delivered = new int[this.weeks.length];
		int running = 0;
		for (int week = 0; week < this.weeks.length; week++) {
			running += this.weeks[week];
			delivered[week] = running;
		}
		return delivered;
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
	 * When a backlog of this size runs out, by resampling the weeks this team actually
	 * had.
	 *
	 * <p>
	 * <strong>A bootstrap and not a fitted distribution</strong>, which is the point. A
	 * Poisson fit is the obvious alternative and it asserts something false about every
	 * real team: that weeks are independent draws of a single rate, so no week is ever
	 * four deviations out because the build broke. Resampling asserts only that future
	 * weeks look like some multiset of past ones — and unlike a fit it has an oracle,
	 * because a team that finished exactly five a week for twenty weeks must answer
	 * exactly eight weeks for forty items, in every run, with no spread at all.
	 *
	 * <p>
	 * <strong>It cannot produce a week worse than {@link #worst()}, and that is the
	 * largest thing to know about the answer.</strong> Simulated against a team that
	 * loses one week in ten to an incident, 23% of teams at a quarter of history have
	 * never observed their own bad week — and theirs is the forecast that comes back
	 * early and confident, which is the one direction this product exists to correct.
	 * Nothing here compensates for it: inventing a tail nobody observed would be a number
	 * with no source inside a forecast whose entire claim is that it came from the team.
	 * What ships instead is the window, so a reader who knows their team can see whether
	 * it contains the week they are worried about.
	 *
	 * <p>
	 * {@link Random} rather than anything faster, for {@code Engine}'s reason: its
	 * algorithms are written into its contract rather than only its implementation, so a
	 * seed still means the same thing after a JDK upgrade.
	 * @param remaining how much is left to finish, which must be something — a plan with
	 * nothing left is not a forecast of no weeks, it is a plan with nothing left, and
	 * saying which is the caller's job
	 * @param runs how many times to resample; ten thousand is what the engine uses
	 * @param seed the whole of what makes an answer reproducible
	 * @throws IllegalArgumentException if there is nothing left to finish, or no runs to
	 * make
	 * @throws IllegalStateException if this team has never finished anything, since no
	 * amount of resampling weeks of nothing covers a backlog
	 */
	public ThroughputForecast project(int remaining, int runs, long seed) {
		if (remaining <= 0) {
			throw new IllegalArgumentException("A backlog of " + remaining + " is not a question about when");
		}
		if (runs <= 0) {
			throw new IllegalArgumentException("A forecast needs runs, and was asked for " + runs);
		}
		if (completed() == 0) {
			throw new IllegalStateException("Nothing has ever been finished, so no backlog ever runs out");
		}
		RandomGenerator random = new Random(seed);
		int[] finishes = new int[runs];
		// How many runs had covered the backlog exactly at each week, which is what tells
		// the trajectory below where the runs that are no longer running have got to.
		int[] finishedInWeek = new int[MOST_WEEKS + 1];
		List<int[]> delivered = new ArrayList<>();
		int unfinished = 0;
		for (int run = 0; run < runs; run++) {
			int done = 0;
			int week = 0;
			while (done < remaining && week < MOST_WEEKS) {
				done += this.weeks[random.nextInt(this.weeks.length)];
				week++;
				while (delivered.size() < week) {
					delivered.add(new int[remaining + 1]);
				}
				// Capped, because a week that overshoots delivers the backlog and not
				// more: a burn-up that rose above its own total would be reporting work
				// nobody has.
				delivered.get(week - 1)[Math.min(done, remaining)]++;
			}
			if (done < remaining) {
				unfinished++;
			}
			finishes[run] = week;
			finishedInWeek[week]++;
		}
		// Sorted once and read twice: the percentiles are order statistics of it, and so
		// is how far ahead the trajectory below is worth drawing.
		int[] sorted = finishes.clone();
		Arrays.sort(sorted);
		// **A run that never covered the backlog has no route worth drawing**, and its
		// percentiles are censored at the horizon for the same reason — so a plan in that
		// state reports no trajectory rather than one that climbs for ten years and never
		// arrives. It is also where building one would cost most: every week to the
		// horizon,
		// each of them read, for a picture that says a plan does not finish.
		List<Delivered> route = (unfinished > 0) ? List.of()
				: trajectory(delivered, finishedInWeek, runs, remaining, at(sorted, 0.95));
		return summarise(finishes, sorted, unfinished, route);
	}

	/**
	 * The route the runs took, as a band per week.
	 *
	 * <p>
	 * <strong>It takes no draw of its own</strong>, which is {@code RunObserver}'s
	 * argument arriving in a smaller place: every number here is one the loop above
	 * already produced, so a trajectory added to this method moves no percentile and no
	 * seeded answer anybody was given before it existed. A test asserts exactly that.
	 *
	 * <p>
	 * <strong>Counted into a histogram per week rather than kept per run.</strong> The
	 * obvious shape is a run-by-week matrix sorted a column at a time, and at ten
	 * thousand runs against the ten-year horizon that is five million integers. A count
	 * of how many runs stood at each delivered figure is bounded by the backlog instead,
	 * and answers the same order statistic. Those counts are accumulated by the loop
	 * above whether or not anybody reads them; what this method does on top of them is
	 * skipped entirely for a plan whose runs did not finish, which is the one case where
	 * the answer is thrown away unread.
	 *
	 * <p>
	 * It is drawn as far as {@code p95Weeks}, which is the last week any figure this
	 * product publishes lands on. The cone's own low edge closes onto the backlog at
	 * {@code p90Weeks}, so the picture shows it close rather than cutting it off in mid
	 * air.
	 */
	private static List<Delivered> trajectory(List<int[]> delivered, int[] finishedInWeek, int runs, int remaining,
			int lastWeek) {
		int horizon = Math.min(lastWeek, delivered.size());
		List<Delivered> weeks = new ArrayList<>(horizon + 1);
		// Week zero is the question being asked, and nothing has been delivered into it.
		weeks.add(new Delivered(0, 0, 0, 0));
		int alreadyFinished = 0;
		for (int week = 1; week <= horizon; week++) {
			alreadyFinished += finishedInWeek[week - 1];
			int[] standing = delivered.get(week - 1).clone();
			// A run that finished earlier stopped being counted, and is standing on the
			// backlog rather than missing from the week.
			standing[remaining] += alreadyFinished;
			weeks
				.add(new Delivered(week, at(standing, runs, 0.10), at(standing, runs, 0.50), at(standing, runs, 0.90)));
		}
		return weeks;
	}

	/**
	 * The figure that share of the runs had reached, read off a count of how many stood
	 * at each.
	 *
	 * <p>
	 * The same nearest rank {@link #at(int[], double)} takes, and it has to be: a cone
	 * read one way beside a date read another would disagree about its own plan in the
	 * fourth week and nobody could say why.
	 *
	 * <p>
	 * <strong>The walk has no fallback and must not grow one.</strong> Every run stands
	 * somewhere in every week — still running and counted where it got to, or finished
	 * and added to the backlog by the caller — so these counts always sum to {@code runs}
	 * and a rank below that is always reached. A guard for the case where it is not would
	 * be a branch no test could ever cover, which is the hole in the coverage gate
	 * {@code WorkItemService.requireConsistent} refuses a {@code switch} over an enum
	 * for.
	 */
	private static int at(int[] standing, int runs, double share) {
		int rank = Math.max(0, Math.min((int) Math.ceil(share * runs) - 1, runs - 1));
		int counted = 0;
		int figure = -1;
		while (counted <= rank) {
			figure++;
			counted += standing[figure];
		}
		return figure;
	}

	/**
	 * <strong>The mean and the deviation are summed in the order the runs happened, and
	 * the percentiles are read off the same figures sorted.</strong> Both arrays are here
	 * for that reason and one of them would have been tidier: summing the sorted copy
	 * instead moves the standard deviation in its eleventh decimal, because
	 * floating-point addition is not associative and every run is added to a different
	 * running total. Nothing a reader could see would change, which is exactly why it is
	 * worth a parameter — an answer this product has already given must not move because
	 * somebody removed a copy of an array.
	 */
	private static ThroughputForecast summarise(int[] finishes, int[] sorted, int unfinished,
			List<Delivered> trajectory) {
		double total = 0.0;
		for (int finish : finishes) {
			total += finish;
		}
		double mean = total / finishes.length;
		double squares = 0.0;
		for (int finish : finishes) {
			squares += (finish - mean) * (finish - mean);
		}
		// Divided by the count rather than one less than it, as the engine divides: at
		// ten
		// thousand runs the difference is far under the sampling error either way, and it
		// means a single run answers with zero instead of a NaN.
		double deviation = Math.sqrt(squares / finishes.length);
		return new ThroughputForecast(mean, deviation, at(sorted, 0.10), at(sorted, 0.50), at(sorted, 0.80),
				at(sorted, 0.90), at(sorted, 0.95), unfinished, trajectory);
	}

	/**
	 * The week by which that share of the runs had finished.
	 *
	 * <p>
	 * Nearest rank and no interpolation, which is {@code Engine}'s convention and has to
	 * be: two answers read at the same confidence must be read the same way, or the gap
	 * between them is partly a difference in how each was rounded.
	 */
	private static int at(int[] sorted, double share) {
		int index = (int) Math.ceil(share * sorted.length) - 1;
		return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
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
