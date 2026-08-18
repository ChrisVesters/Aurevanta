package com.cvesters.aurevanta.forecast;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a throughput forecast did not do, said in codes rather than in prose.
 *
 * <p>
 * <strong>Its own enum and not {@link ForecastLimitation}'s, because that one is
 * stored.</strong> A run's limitations live in {@code forecast_runs.outputs} and are read
 * back years later, which is why nothing is ever deleted from it. Nothing here is written
 * down at all — a throughput answer is derived from dated history every time it is asked
 * for — so the two have different lifetimes and different rules about changing, and one
 * enum would have given the looser of them to the stricter.
 *
 * <p>
 * Codes rather than sentences, translated by the frontend like every other code this API
 * publishes.
 */
public enum ThroughputLimitation {

	/**
	 * <strong>Unconditional, and it is the one `roadmap.md` gets wrong.</strong> That
	 * document says throughput "implicitly absorbs interruptions, holidays, scope growth,
	 * and the fact that nobody works eight focused hours". Three of the four are right.
	 * What the history absorbs is the <em>drag</em> of past discovered work — a team that
	 * closed five a week while two in ten were unlisted has a rate of five, earned partly
	 * on work nobody had written down. Projecting the items you can see at that rate is
	 * optimistic by exactly that share, and nothing in this schema records which items
	 * were discovered mid-flight, so it cannot be measured here. It is said instead, on
	 * every answer.
	 */
	EXCLUDES_UNLISTED_WORK("throughput_excludes_unlisted_work"),

	/**
	 * Fewer weeks than {@code Throughput.WORTH_SHOWING}, so there is no projection at all
	 * — only the window. Simulated, one month of history answers anywhere between seven
	 * weeks and thirteen against a truth of ten, which is not a wide forecast but a
	 * random one.
	 */
	HISTORY_TOO_SHORT("throughput_history_too_short"),

	/**
	 * Enough to project from, not enough to leave unremarked — between
	 * {@code Throughput.WORTH_SHOWING} and {@code WORTH_TRUSTING}. At a quarter of
	 * history, 23% of teams that lose a week every ten have not yet observed one of their
	 * own, and a bootstrap cannot draw a week worse than the worst it has seen.
	 */
	WINDOW_IS_SHORT("throughput_window_is_short"),

	/**
	 * Nothing is left to finish, so there is no question about when. Not a forecast of no
	 * weeks: a plan with nothing in it and a plan that is finished are different answers.
	 */
	NOTHING_LEFT("throughput_nothing_left"),

	/**
	 * At this rate the backlog does not run out inside {@code Throughput.MOST_WEEKS}, so
	 * the percentiles would every one of them stand at the horizon. They are withheld
	 * rather than published, because a censored number rendered as a date is read as a
	 * date.
	 */
	BEYOND_HORIZON("throughput_beyond_horizon");

	private final String code;

	ThroughputLimitation(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return this.code;
	}

}
