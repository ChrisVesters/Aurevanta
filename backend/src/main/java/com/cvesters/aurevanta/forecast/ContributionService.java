package com.cvesters.aurevanta.forecast;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.forecast.model.Contributions;
import com.cvesters.aurevanta.forecast.model.ScopeGrowth;
import com.cvesters.aurevanta.forecast.model.TeamFactor;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.problem.ForecastNotFoundException;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;
import com.cvesters.aurevanta.problem.NotAMemberException;

/**
 * What a plan holds, ranked by how much each of it widened the forecast — M6.
 *
 * <p>
 * <strong>Nothing is stored, and that is what makes it work on the past.</strong> The
 * per-item durations come from replaying the run out of its own seed, which is what M3a
 * kept a seed for. A stored contribution would only ever explain runs made after the
 * column existed; a derived one explains every forecast this product has ever produced.
 *
 * <p>
 * Its own service rather than another method on {@link ForecastService}, which is the
 * arrangement {@link MovementService} and {@link ThroughputService} already keep: this
 * reads a run and writes nothing, where that one makes them.
 */
@Service
class ContributionService {

	private final ForecastService forecasts;

	private final PlanTitles titles;

	ContributionService(ForecastService forecasts, PlanTitles titles) {
		this.forecasts = forecasts;
		this.titles = titles;
	}

	/**
	 * What a stored run's spread turned out to be made of, ranked.
	 *
	 * <p>
	 * <strong>Nothing was stored for this and nothing is stored by it.</strong> The
	 * durations a ranking needs are a per-item, per-run number — five million of them for
	 * a plan at the scale this product supports — so the run is replayed from its seed
	 * instead, which is what M3a kept a seed for. The payoff is not the space saved: a
	 * stored column would only ever have explained runs made after it existed, and this
	 * explains every forecast this product has ever produced.
	 *
	 * <p>
	 * <strong>And the replay has to prove it is the same engine before it may explain
	 * anything.</strong> A run keeps the six figures it produced, so the replay's own six
	 * are compared against them and any difference refuses the whole thing. That costs
	 * six comparisons after ten thousand simulations and it is the only guard there is: a
	 * ranking produced by a different model is not a rougher ranking of this plan, it is
	 * an exact ranking of a plan nobody forecast, and it would look entirely reasonable.
	 *
	 * <p>
	 * <strong>A source nobody modelled gets no row at all, rather than a row reading
	 * zero.</strong> It never varied, so it measures as nothing either way — but zero
	 * invites a reader to conclude their team has no common cause, when what they did was
	 * decline to model one. Decided from what the run stored, not from what it drew,
	 * which is the rule a run made before there was a calendar already follows.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ForecastNotFoundException if no run in it has that identifier
	 * @throws ForecastReplayMismatchException if replaying it does not reproduce it
	 */
	@Transactional(readOnly = true)
	public List<ContributionResponse> forRun(UUID callerId, UUID tenantId, UUID runId) {
		ForecastRun run = this.forecasts.get(callerId, tenantId, runId);
		ForecastInputs inputs = this.forecasts.inputsOf(run);
		Contributions measured = Contributions.forRun(inputs.items().size());
		// The same replay an inverse query makes, through the same method: two ways of
		// re-running one stored forecast would eventually be one right way and one that
		// had
		// stopped being told about a parameter.
		ForecastReplays.requireReproduces(run, ForecastReplays.replay(run, inputs, inputs.toModels(), measured));

		Map<UUID, WorkItem> named = this.titles.in(callerId, tenantId, run.getProject().getId());

		List<ContributionResponse> ranked = new ArrayList<>(inputs.items().size() + 2);
		for (int at = 0; at < inputs.items().size(); at++) {
			UUID itemId = inputs.items().get(at).id();
			WorkItem still = named.get(itemId);
			ranked.add(ContributionResponse.of(itemId, PlanTitles.titleOf(still), PlanTitles.isArchived(still),
					measured.ofItem(at)));
		}
		if (!ScopeGrowth.NONE.equals(ForecastReplays.scopeGrowthOf(run))) {
			ranked.add(ContributionResponse.of(ContributionKind.DISCOVERED_WORK, measured.ofDiscoveredWork()));
		}
		if (!TeamFactor.NONE.equals(ForecastReplays.teamFactorOf(run))) {
			ranked.add(ContributionResponse.of(ContributionKind.TEAM_FACTOR, measured.ofTeamFactor()));
		}
		// Ranked here rather than by whoever draws it: the order *is* the feature, and a
		// second caller sorting it a second way would be a second answer to one question.
		ranked.sort(Comparator.comparingDouble(ContributionResponse::shareOfSpread).reversed());
		return List.copyOf(ranked);
	}

}
