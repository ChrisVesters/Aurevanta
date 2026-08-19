package com.cvesters.aurevanta.requirement;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.problem.DuplicateRequirementException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.RequirementExceedsPoolException;
import com.cvesters.aurevanta.problem.ResourceNotFoundException;
import com.cvesters.aurevanta.problem.WorkItemNotFoundException;
import com.cvesters.aurevanta.project.ProjectService;
import com.cvesters.aurevanta.resource.Resource;
import com.cvesters.aurevanta.resource.ResourceService;

/**
 * What each piece of work needs, and the one way it is written.
 *
 * <p>
 * <strong>A whole set at a time, and never one line of it.</strong> A requirement means
 * little alone — what a task needs is the list, and a screen edits the list — so there is
 * no endpoint that adds one and none that removes one. Replacing the set makes "it needs
 * these two things now" a single fact arriving once, rather than a sequence a reader has
 * to reassemble from an add, a change and a delete.
 *
 * <p>
 * <strong>It reaches the item and the pool through their services rather than their
 * tables</strong>, which is the rule {@code forecast} keeps for the same reason: whether
 * a caller may see a piece of work is asked of {@code WorkItemService}, so the membership
 * check is that one rule reached rather than a second copy that can drift from it.
 */
@Service
public class RequirementService {

	private final RequirementRepository requirements;

	private final WorkItemService items;

	private final ResourceService resources;

	private final ProjectService projects;

	private final Clock clock;

	RequirementService(RequirementRepository requirements, WorkItemService items, ResourceService resources,
			ProjectService projects, Clock clock) {
		this.requirements = requirements;
		this.items = items;
		this.resources = resources;
		this.projects = projects;
		this.clock = clock;
	}

	/**
	 * What one piece of work needs today.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no work item in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<Requirement> listForItem(UUID callerId, UUID tenantId, UUID itemId) {
		WorkItem item = this.items.get(callerId, tenantId, itemId);
		return this.requirements.findAllForItem(item.getId(), tenantId);
	}

	/**
	 * Everything a whole plan needs, which is what a screen showing that plan asks for.
	 *
	 * <p>
	 * Fetched through {@code ProjectService} rather than assumed, so that asking about a
	 * plan that is not there is {@code project_not_found} rather than an empty list — "no
	 * such plan" and "a plan needing nothing" are different answers and only one of them
	 * is worth acting on.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<Requirement> listInProject(UUID callerId, UUID tenantId, UUID projectId) {
		this.projects.get(callerId, tenantId, projectId);
		return this.requirements.findAllInProject(projectId, tenantId);
	}

	/**
	 * Says what a piece of work needs, in place of whatever it needed before.
	 *
	 * <p>
	 * <strong>An empty list is a claim rather than a mistake</strong>, and it is how
	 * somebody says this is generic work anybody can pick up. It is also what the
	 * scheduler reads as one unit of whichever pool has one free, so clearing a
	 * requirement is not the same as removing the item from the competition for a team.
	 *
	 * <p>
	 * <strong>Archived pools may still be named.</strong> Putting a pool away says a team
	 * no longer has it, and a plan that still says a task needs it is a plan worth
	 * forecasting honestly rather than one to refuse — the scheduler is where that is
	 * answered, not here.
	 * @param wanted what it needs, at most one line per pool
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no work item in it has that identifier
	 * @throws ResourceNotFoundException if a named pool is not in this organisation
	 * @throws DuplicateRequirementException if a pool is named more than once
	 */
	@Transactional
	public List<Requirement> replaceForItem(UUID callerId, UUID tenantId, UUID itemId, List<RequiredUnits> wanted) {
		// Decided from the request alone and before anything is looked up, the way a
		// self-dependency is: a body naming one pool twice is wrong whatever else exists,
		// and a caller who sent it learns nothing about which pools are there by hearing
		// so.
		requireEachPoolOnce(wanted);
		WorkItem item = this.items.get(callerId, tenantId, itemId);
		List<Resource> pools = new ArrayList<>(wanted.size());
		for (RequiredUnits line : wanted) {
			Resource pool = this.resources.get(callerId, tenantId, line.resourceId());
			// Work that needs more of a pool than the pool holds never starts, in any run
			// at any moment — so it is not a plan that forecasts badly, it is a plan with
			// no schedule. Refused here where the person can see the bound, and refused
			// again where a forecast is asked for, because shrinking the pool afterwards
			// reaches the same state by a door this check cannot watch.
			if (line.units() > pool.getUnits()) {
				throw new RequirementExceedsPoolException();
			}
			pools.add(pool);
		}
		// Cleared and written rather than diffed. A requirement carries no history
		// anything
		// downstream reads — a run copies the declaration it was scheduled under — so the
		// simplest correct thing is also the honest one.
		this.requirements.deleteAllForItem(item.getId(), tenantId);
		Instant now = Instant.now(this.clock);
		for (int at = 0; at < wanted.size(); at++) {
			this.requirements.save(new Requirement(item.getTenant(), item, pools.get(at), wanted.get(at).units(), now));
		}
		// Read back rather than handed back in the order it arrived: what a caller is
		// shown
		// is the order the pools were declared in, because that order is part of a
		// modelling
		// rule and not a presentation choice.
		return this.requirements.findAllForItem(item.getId(), tenantId);
	}

	private static void requireEachPoolOnce(List<RequiredUnits> wanted) {
		Set<UUID> named = new HashSet<>();
		for (RequiredUnits line : wanted) {
			if (!named.add(line.resourceId())) {
				throw new DuplicateRequirementException();
			}
		}
	}

	/**
	 * One line of what a piece of work needs, as the service takes it — a pool and how
	 * many of it.
	 */
	public record RequiredUnits(UUID resourceId, int units) {
	}

}
