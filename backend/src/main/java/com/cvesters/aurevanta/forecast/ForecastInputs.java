package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.cvesters.aurevanta.forecast.model.ItemModel;
import com.cvesters.aurevanta.forecast.model.LogNormalFit;
import com.cvesters.aurevanta.forecast.model.Precedence;
import com.cvesters.aurevanta.forecast.model.Resourcing;
import com.cvesters.aurevanta.item.WorkItemStatus;

/**
 * Everything a forecast was given, kept exactly as it was given.
 *
 * <p>
 * <strong>A value, not a set of references.</strong> Pointing at the live rows would not
 * do: items get reworded, arrows get rubbed out and progress changes daily, so within a
 * week a run would describe something that no longer exists. M10's movement decomposition
 * — why the date moved, split into scope added, estimates revised and work completed — is
 * a diff of two of these, and there is nothing to diff if both sides moved.
 *
 * <p>
 * <strong>Raw ranges rather than fitted parameters.</strong> What is stored is what
 * people typed, so the fit stays an implementation detail that a version bump is allowed
 * to redefine — and so a diff between two runs reads as "somebody revised their estimate"
 * rather than as two numbers nobody recognises.
 *
 * <p>
 * <strong>Identifiers rather than positions</strong>, unlike {@link Precedence}, which
 * the engine wants as array offsets. A position means nothing once the plan has changed,
 * and a diff needs to know that this item is the same item.
 */
public record ForecastInputs(List<PlannedItem> items, List<PlannedEdge> edges, List<PlannedPool> pools,
		List<PlannedNeed> needs) {

	/**
	 * <strong>A run made before there was a team to describe has neither of the last two,
	 * and reads back with both empty.</strong> Jackson hands a missing field back as
	 * null, and a snapshot written in July is not going to grow one — so the absence is
	 * normalised here rather than checked at every reader. An empty declaration is
	 * exactly what {@code Engine.VERSION} 2 assumed: one pool of the capacity the run
	 * stored, and nothing named.
	 */
	public ForecastInputs {
		pools = (pools != null) ? pools : List.of();
		needs = (needs != null) ? needs : List.of();
	}

	/**
	 * The plan as it stood, and the team as it stood — which are two different things.
	 */
	public ForecastInputs(List<PlannedItem> items, List<PlannedEdge> edges) {
		this(items, edges, List.of(), List.of());
	}

	/**
	 * One piece of work as the forecast saw it.
	 *
	 * @param estimates one per estimator, in the order they were read — which is what
	 * makes a replay pick the same estimator in the same run as the original did.
	 */
	public record PlannedItem(UUID id, WorkItemStatus status, BigDecimal spentHours, List<PlannedEstimate> estimates) {
	}

	/**
	 * One pool the plan was scheduled against, in the order it was declared.
	 *
	 * <p>
	 * <strong>An identifier and a number, and no name.</strong> The name comes off the
	 * organisation's own list when somebody reads the run, which is the rule M6 already
	 * keeps for the titles of the work it ranks: a pool renamed since is not a thing that
	 * moved, and a snapshot holding the old name would make it look like one.
	 *
	 * <p>
	 * The order is the model rather than the presentation. Work that names nothing takes
	 * one unit of the first pool with one free, so a run replayed against a differently
	 * ordered list would schedule differently.
	 */
	public record PlannedPool(UUID resourceId, int units) {
	}

	/**
	 * One thing a piece of work needed, as the forecast saw it.
	 *
	 * <p>
	 * Kept beside the items rather than inside them, and that is not only convenience: a
	 * decomposition rebuilds the item list twice — once with the newer progress and once
	 * with the newer estimates — and a requirement belongs to neither of those questions.
	 * Held here it is untouched by both, and moves with the plan when the plan does.
	 */
	public record PlannedNeed(UUID workItemId, UUID resourceId, int units) {
	}

	/** One person's range, as they typed it. */
	public record PlannedEstimate(UUID estimatorId, BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p90Hours) {
	}

	/**
	 * One arrow. Only ever holds edges whose both ends are among the items above — the
	 * ones pointing at work that has been put away are dropped before they get here, and
	 * reported as {@link ForecastLimitation#DEPENDENCIES_ON_ARCHIVED_WORK} rather than
	 * quietly left out.
	 */
	public record PlannedEdge(UUID predecessorItemId, UUID successorItemId, BigDecimal lagHours) {
	}

	/**
	 * The plan as the engine takes it, fitted.
	 *
	 * <p>
	 * Used both to run a forecast and to replay a stored one, which is the whole of what
	 * makes the replay meaningful: if these were two pieces of code, a test that they
	 * agree would be a test of the copy rather than of the record.
	 */
	public List<ItemModel> toModels() {
		List<ItemModel> models = new ArrayList<>(this.items.size());
		for (PlannedItem item : this.items) {
			List<LogNormalFit> fits = new ArrayList<>(item.estimates().size());
			for (PlannedEstimate estimate : item.estimates()) {
				fits.add(LogNormalFit.from(estimate.p10Hours().doubleValue(), estimate.p90Hours().doubleValue()));
			}
			models.add(new ItemModel(item.id(), fits, item.status(), spent(item)));
		}
		return models;
	}

	/** The same arrows, turned into the array offsets the scheduler walks. */
	public List<Precedence> toPrecedences() {
		Map<UUID, Integer> position = new HashMap<>();
		for (int at = 0; at < this.items.size(); at++) {
			position.put(this.items.get(at).id(), at);
		}
		List<Precedence> precedences = new ArrayList<>(this.edges.size());
		for (PlannedEdge edge : this.edges) {
			precedences.add(new Precedence(position.get(edge.predecessorItemId()), position.get(edge.successorItemId()),
					edge.lagHours().doubleValue()));
		}
		return precedences;
	}

	/**
	 * The team as the scheduler takes it, and what each piece of work needs of it.
	 *
	 * <p>
	 * <strong>A run with no declaration is one pool of the capacity it stored</strong>,
	 * which is the whole of what lets {@code Engine.VERSION} 3 contain version 2: the
	 * same decisions in the same order, not an approximation of them. Every forecast this
	 * product made before M11 reads back through this line.
	 * @param capacity what the run stored, which is the size of the only pool when there
	 * was no team to describe and the sum of the declared units when there was
	 */
	public Resourcing toResourcing(int capacity) {
		if (this.pools.isEmpty()) {
			return Resourcing.pooled(capacity, this.items.size());
		}
		Map<UUID, Integer> pool = new HashMap<>();
		int[] units = new int[this.pools.size()];
		for (int at = 0; at < this.pools.size(); at++) {
			pool.put(this.pools.get(at).resourceId(), at);
			units[at] = this.pools.get(at).units();
		}
		Map<UUID, Integer> position = new HashMap<>();
		for (int at = 0; at < this.items.size(); at++) {
			position.put(this.items.get(at).id(), at);
		}
		int[][] needed = new int[this.items.size()][this.pools.size()];
		for (PlannedNeed need : this.needs) {
			needed[position.get(need.workItemId())][pool.get(need.resourceId())] = need.units();
		}
		return Resourcing.of(units, needed);
	}

	/** Nobody measured it, which is the ordinary case and means none was spent. */
	private static double spent(PlannedItem item) {
		return (item.spentHours() != null) ? item.spentHours().doubleValue() : 0.0;
	}

}
