package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.forecast.model.Throughput;
import com.cvesters.aurevanta.forecast.model.ThroughputForecast;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.ThroughputOutOfOrderException;

/**
 * What a plan's own history says about when it will be finished.
 *
 * <p>
 * <strong>It reads and stores nothing</strong>, and unlike M6's replays that is not even
 * a decision so much as an observation: the history is already in the database, already
 * dated, and already required on every finished item, so an answer as of any day is
 * reproducible from it. A row here would be a cached answer to a question that costs four
 * queries and a ten-thousand-run loop over a few hundred integers. It also keeps
 * {@code forecast_runs} meaning one thing — somebody asked the engine — which is what
 * M10's detector walks.
 *
 * <p>
 * <strong>This plan's history and not the organisation's.</strong> An organisation's rate
 * is the sum of what it finished everywhere; applying it to one plan's backlog assumes
 * the team spends all of that rate here, so a team running three plans would get three
 * forecasts each assuming it has the whole team. Correcting that needs to know how
 * attention is split and nothing in this schema records it — there are no assignments and
 * no allocations until M11. The cost is that a young plan gets a window and no forecast,
 * which is the answer M8 gives an organisation that has finished nothing, and is better
 * than a confident number resting on a split nobody stated.
 */
@Service
public class ThroughputService {

	/**
	 * What the engine uses, and fixed rather than asked for.
	 *
	 * <p>
	 * This endpoint takes a date and nothing else. The engine asks for five assumptions
	 * and every one of them is a claim somebody has to make; the whole value of the
	 * comparison is that this side asks for none, so a knob here would be spending the
	 * thing that makes it worth reading.
	 */
	private static final int SAMPLE_COUNT = 10_000;

	private final WorkItemService items;

	ThroughputService(WorkItemService items) {
		this.items = items;
	}

	/**
	 * The plan's history, and what it says about the work left in it.
	 * @param asOf the day being asked about, stated by the caller because what day it is
	 * where somebody is sitting is not something a server knows — the argument
	 * {@code starts_on} makes on a forecast run
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 * @throws ThroughputOutOfOrderException if the plan finished something after that day
	 */
	@Transactional(readOnly = true)
	public ThroughputResponse forecastFor(UUID callerId, UUID tenantId, UUID projectId, LocalDate asOf) {
		List<LocalDate> completions = this.items.completionsIn(callerId, tenantId, projectId);
		int remaining = this.items.remainingIn(callerId, tenantId, projectId);
		Throughput history = historyOf(completions, asOf);

		List<ThroughputLimitation> limitations = new ArrayList<>();
		// Unconditional, and the one `roadmap.md` gets wrong: a rate earned partly on
		// work
		// nobody had listed does not cover the listed work at that rate.
		limitations.add(ThroughputLimitation.EXCLUDES_UNLISTED_WORK);
		ThroughputProjectionResponse projection = project(history, remaining, projectId, asOf, limitations);
		return new ThroughputResponse(projectId, asOf, Throughput.RULE, remaining, ThroughputWindowResponse.of(history),
				projection, List.copyOf(limitations));
	}

	/**
	 * The projection, or nothing and a reason.
	 *
	 * <p>
	 * <strong>Three ways there is no answer, and each says which.</strong> Nothing left
	 * to deliver is not a forecast of no weeks; too little history is a random answer
	 * rather than a wide one; and a rate that does not clear the backlog inside the
	 * horizon would put every percentile on the horizon itself, which is a censored
	 * number that reads as a date. In all three the window still ships, because the
	 * window is what a reader can judge for themselves.
	 */
	private static ThroughputProjectionResponse project(Throughput history, int remaining, UUID projectId,
			LocalDate asOf, List<ThroughputLimitation> limitations) {
		if (remaining == 0) {
			limitations.add(ThroughputLimitation.NOTHING_LEFT);
			return null;
		}
		if (!history.worthShowing()) {
			limitations.add(ThroughputLimitation.HISTORY_TOO_SHORT);
			return null;
		}
		if (!history.worthTrusting()) {
			limitations.add(ThroughputLimitation.WINDOW_IS_SHORT);
		}
		long seed = seedFor(projectId, asOf, remaining);
		ThroughputForecast forecast = history.project(remaining, SAMPLE_COUNT, seed);
		if (forecast.unfinishedRuns() > 0) {
			limitations.add(ThroughputLimitation.BEYOND_HORIZON);
			return null;
		}
		return ThroughputProjectionResponse.of(forecast, asOf, seed, SAMPLE_COUNT);
	}

	/**
	 * A history, or a refusal if the question is about a day some of it happened after.
	 *
	 * <p>
	 * {@code Throughput.of} refuses that as a fact about its input; here it becomes a
	 * code a caller can act on rather than a five hundred. Nothing else in this method
	 * can fail that way, so the catch is narrow on purpose.
	 */
	private static Throughput historyOf(List<LocalDate> completions, LocalDate asOf) {
		if (!completions.isEmpty() && completions.get(completions.size() - 1).isAfter(asOf)) {
			throw new ThroughputOutOfOrderException();
		}
		return Throughput.of(completions, asOf);
	}

	/**
	 * The same question gives the same answer.
	 *
	 * <p>
	 * <strong>Derived rather than random, and not overridable.</strong> A run stores its
	 * seed because it is written down once and replayed years later; nothing here is
	 * written down, so what reproducibility means is that asking twice agrees — and a
	 * seed computed from the question gives that without a knob. It is still
	 * <em>published</em>, so an answer can be explained; it is simply not something to
	 * configure, which is the rule this endpoint keeps everywhere else.
	 */
	private static long seedFor(UUID projectId, LocalDate asOf, int remaining) {
		long seed = projectId.getMostSignificantBits() * 31 + projectId.getLeastSignificantBits();
		seed = seed * 31 + asOf.toEpochDay();
		return seed * 31 + remaining;
	}

}
