package com.cvesters.aurevanta.forecast;

import com.cvesters.aurevanta.item.WorkItemService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.cvesters.aurevanta.item.WorkItem;

/**
 * What a run's work is called <em>now</em>, which is not what the run called it.
 *
 * <p>
 * <strong>Titles come off the plan and never off the snapshot</strong>, which holds
 * identifiers and no title on purpose: M10 diffs those snapshots and a rename is not a
 * thing that moved. So every answer that names a run's work — a contribution ranking and
 * a list of cuts — resolves the name against the plan as it stands today.
 *
 * <p>
 * Its own class because the three-way rule is the whole of it: named, named-and-put-away,
 * or no longer in the plan at all. Written twice it would be two chances for one copy to
 * start rendering a missing item as a blank, which is exactly the failure it exists to
 * prevent.
 */
@Component
class PlanTitles {

	private final WorkItemService items;

	PlanTitles(WorkItemService items) {
		this.items = items;
	}

	/**
	 * What the plan calls its work now, live and archived alike.
	 *
	 * <p>
	 * Both listings, because work put away since a run is still work that run was about —
	 * and titles come off the plan rather than the snapshot, which never held one.
	 */
	Map<UUID, WorkItem> in(UUID callerId, UUID tenantId, UUID projectId) {
		Map<UUID, WorkItem> named = new HashMap<>();
		for (WorkItem live : this.items.list(callerId, tenantId, projectId, false)) {
			named.put(live.getId(), live);
		}
		for (WorkItem away : this.items.list(callerId, tenantId, projectId, true)) {
			named.put(away.getId(), away);
		}
		return named;
	}

	/**
	 * What a run's work is called now, or nothing where the plan no longer holds it.
	 *
	 * <p>
	 * Stated once because two answers name the same work — a contribution ranking and a
	 * list of cuts — and three-way logic written twice is two chances for one copy to
	 * start rendering a missing item as a blank.
	 */
	static String titleOf(WorkItem still) {
		return (still != null) ? still.getTitle() : null;
	}

	/** Whether it has been put away since, which is said rather than hidden. */
	static boolean isArchived(WorkItem still) {
		return still != null && still.getArchivedAt() != null;
	}

}
