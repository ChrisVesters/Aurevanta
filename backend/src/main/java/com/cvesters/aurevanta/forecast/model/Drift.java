package com.cvesters.aurevanta.forecast.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Whether a plan's date keeps moving out, or is merely moving.
 *
 * <p>
 * <strong>The direction of the last few runs cannot carry this, and that is
 * measured.</strong> Sampling contributes almost nothing — at ten thousand samples one
 * percentile moves by about a fifth of a working day between seeds, and a date is a whole
 * day — so every movement a history holds is real. But real movement in a plan that is
 * not slipping has no <em>direction</em>: it goes out one week and in the next. A rule
 * reading three successive increases therefore reads a coin, and
 * `docs/design/communicating-a-forecast.md` has the table: it fires on 37% of plans
 * re-forecast weekly for two months, 86% over six, and 98% over a year, on plans that are
 * not slipping at all. Four in a row fires on 83% of them within a year and five on 56%,
 * so <strong>no threshold on direction rescues it</strong>.
 *
 * <p>
 * <strong>What is left is magnitude, and the only honest yardstick is the plan's own
 * band.</strong> Three days of drift on a three-week band is inside the movement the
 * forecast already admits to; three days on a two-day band is the plan coming apart. So
 * the question is how far the chosen percentile has moved <em>since the oldest run that
 * answered the same question</em>, measured against the distance between the current
 * band's two ends.
 *
 * <p>
 * <strong>A run that answered a different question ends the window rather than being
 * skipped.</strong> Somebody who halved the capacity moved the date a fortnight and did
 * not slip, and drift measured across that boundary is `roadmap.md`'s own warning — a
 * slide that never happened. Skipping such a run instead would be worse than either: it
 * would leave a window whose two ends are comparable and whose middle nobody looked at.
 *
 * @param runs how many forecasts the window holds, counting the newest. One means there
 * is nothing behind it to have drifted from, which is an answer rather than a division by
 * zero: the plan has drifted nought days from itself.
 * @param fromDate where the oldest run in the window put the chosen percentile, and
 * {@code toDate} where the newest does. Null together with {@code days} whenever either
 * run has no calendar to read its hours through.
 * @param bandDays the distance between the current band's two ends, which is the
 * yardstick and not a reading of the plan. The <em>current</em> one deliberately: an old
 * run's band is not what anybody is being asked to believe today. <strong>Zero is not a
 * confident plan, it is a short one</strong> — both ends are rounded up to a whole day on
 * their own, so a plan of a few days puts them on the same one.
 * @param movingOut whether the drift is far enough past what this plan already says is
 * possible to be worth saying out loud. Never true for a plan that came in, however far:
 * this answers one question and answering the opposite one in the same field would make
 * the flag unreadable. <strong>Never true without a band to measure against
 * either</strong> — with a yardstick of zero the rule degenerates into "any drift at
 * all", and the plans it degenerates on are precisely the small ones, where
 * `docs/design/communicating-a-forecast.md` step 3 measured re-running alone moving the
 * date by <em>days</em>. The measurement that says this detector needs no noise floor was
 * taken on a twelve-item chain and does not hold there, so a plan with no band to speak
 * of gets no verdict rather than the strictest one in the product.
 */
public record Drift(int runs, LocalDate fromDate, LocalDate toDate, Integer days, Integer bandDays, boolean movingOut) {

	/**
	 * How much of its own band a plan has to have drifted out by before this says so.
	 *
	 * <p>
	 * <strong>Stated once, beside the arithmetic, the way
	 * {@code EstimateQuality.TIGHT_BAND} is</strong> — and the browser is told the flag
	 * rather than the number, so a screen can never come to disagree with the server
	 * about when a plan is sliding.
	 *
	 * <p>
	 * The measurement above is what rules a direction rule out; it does not choose this
	 * number, and pretending otherwise would be dressing a judgement as a finding. What
	 * the judgement is bounded by is the two cases the question is put in: a fifth of a
	 * band is nothing worth interrupting anybody about, and a whole band is a plan nobody
	 * needs telling about. A half fires on a plan drifting a day a week inside ten weeks
	 * of a three-week band, and takes a directionless walk of that size most of a year to
	 * reach by luck — in one direction, since coming in is not reported.
	 */
	public static final double WORTH_SAYING = 0.5;

	/**
	 * One forecast as the detector reads it: what it was asked on, and where it landed.
	 *
	 * @param terms what makes it comparable with the run after it, and nothing about the
	 * work itself — a plan that grew is exactly what this is looking for rather than a
	 * reason to stop looking.
	 * @param date the chosen percentile's day, null for a run with no calendar to read.
	 * @param bandFrom and {@code bandTo} are this run's own P10 and P90 days. Carried on
	 * every reading rather than passed beside the list so that the yardstick is
	 * structurally the newest run's own; only that one is ever read.
	 */
	public record Reading(ForecastTerms terms, LocalDate date, LocalDate bandFrom, LocalDate bandTo) {
	}

	/**
	 * How far the newest of these has moved since the oldest that answered the same
	 * question.
	 * @param newestFirst the plan's runs in the order the API lists them
	 * @throws IllegalArgumentException if there are none — a plan nobody has forecast has
	 * no date to have moved, and answering that with a drift of zero would be a claim
	 * about a plan this has never seen
	 */
	public static Drift since(List<Reading> newestFirst) {
		// Asked first, because it is what refuses an empty history — and a caller who
		// gets
		// an index out of bounds instead has been told nothing about what they did wrong.
		int runs = window(newestFirst);
		Reading now = newestFirst.get(0);
		Reading then = newestFirst.get(runs - 1);
		Integer days = between(then.date(), now.date());
		Integer bandDays = between(now.bandFrom(), now.bandTo());
		boolean movingOut = days != null && bandDays != null && bandDays > 0 && days > WORTH_SAYING * bandDays;
		return new Drift(runs, then.date(), now.date(), days, bandDays, movingOut);
	}

	/**
	 * How many of these answered the same question as the newest, counting it.
	 *
	 * <p>
	 * Published because a window is a fact about the <em>runs</em> and not about any one
	 * percentile: whatever reads three confidences out of one history then has one answer
	 * to ask for rather than three it has to hope agree. It reads {@link Reading#terms}
	 * and nothing else, which is what makes that true.
	 * @throws IllegalArgumentException if there are none
	 */
	public static int window(List<Reading> newestFirst) {
		if (newestFirst.isEmpty()) {
			throw new IllegalArgumentException("a plan with no forecasts has no window");
		}
		ForecastTerms now = newestFirst.get(0).terms();
		int runs = 1;
		while (runs < newestFirst.size() && sameQuestion(newestFirst.get(runs).terms(), now)) {
			runs++;
		}
		return runs;
	}

	/**
	 * Whether an earlier run belongs in this window.
	 *
	 * <p>
	 * The engine version, the calendar and the assumptions, which is {@link Comparison}'s
	 * whole list <em>except</em> the start date — and that exception is the one decision
	 * here worth arguing. A plan re-forecast weekly is started from today every time, so
	 * counting a moved start as a different question would end every window at one run
	 * and the detector would never fire at all. It is also the wrong question: what a
	 * reader committed to is a finish date, and a plan whose start moved a week while its
	 * finish stayed put is a plan that delivered a week's work. A finish that moved out
	 * with the start is the thing this exists to notice, not a distortion to correct for.
	 *
	 * <p>
	 * Each run is compared with the newest rather than with the one after it. The two are
	 * the same window, since everything compared here is an equality and equalities are
	 * transitive, and this way says what the window <em>is</em>: the runs that answered
	 * the question being read today.
	 */
	private static boolean sameQuestion(ForecastTerms earlier, ForecastTerms now) {
		Comparison comparison = Comparison.between(earlier, now);
		return comparison.comparable() && comparison.sameCalendar() && comparison.sameAssumptions();
	}

	/**
	 * Whole days between two days, and never hours converted into them: each end was
	 * rounded up to a day on its own, so a count taken from the hours disagrees with the
	 * dates a reader is looking at about half the time.
	 */
	private static Integer between(LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			return null;
		}
		return (int) ChronoUnit.DAYS.between(from, to);
	}

}
