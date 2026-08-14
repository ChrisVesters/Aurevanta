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
public record ForecastInputs(List<PlannedItem> items, List<PlannedEdge> edges) {

	/**
	 * One piece of work as the forecast saw it.
	 *
	 * @param estimates one per estimator, in the order they were read — which is what
	 * makes a replay pick the same estimator in the same run as the original did.
	 */
	public record PlannedItem(UUID id, WorkItemStatus status, BigDecimal spentHours, List<PlannedEstimate> estimates) {
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

	/** Nobody measured it, which is the ordinary case and means none was spent. */
	private static double spent(PlannedItem item) {
		return (item.spentHours() != null) ? item.spentHours().doubleValue() : 0.0;
	}

}
