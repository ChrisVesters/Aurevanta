package com.cvesters.aurevanta.dependency;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.CrossProjectDependencyException;
import com.cvesters.aurevanta.problem.DependencyAlreadyExistsException;
import com.cvesters.aurevanta.problem.DependencyCycleException;
import com.cvesters.aurevanta.problem.DependencyNotFoundException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.SelfDependencyException;
import com.cvesters.aurevanta.problem.WorkItemNotFoundException;
import com.cvesters.aurevanta.project.ProjectService;

/**
 * Joining a plan up, which is what turns a list of work into a shape M3 can forecast.
 *
 * <p>
 * <strong>Finish-to-start with a lag, and nothing else</strong> (decision 4). The other
 * three edge types multiply the scheduler's complexity for cases most teams never draw.
 *
 * <p>
 * Any member may draw an edge or remove one, as with everything else in a plan: roles
 * govern administration only. Unlike a project or an item, an edge <em>is</em> removed
 * rather than archived — it carries no history anything downstream reads, and a line
 * somebody drew by mistake would otherwise stay in the graph for the scheduler to obey
 * forever.
 *
 * <p>
 * Depends on {@code item} and {@code project} and never the other way round, as
 * {@code estimate} does: whether a caller may reach a piece of work is that package's
 * rule, and a second copy of it here would be a second chance for one of them to drift.
 */
@Service
public class DependencyService {

	private final DependencyRepository dependencies;

	private final WorkItemService items;

	private final ProjectService projects;

	private final MembershipService memberships;

	private final Clock clock;

	DependencyService(DependencyRepository dependencies, WorkItemService items, ProjectService projects,
			MembershipService memberships, Clock clock) {
		this.dependencies = dependencies;
		this.items = items;
		this.projects = projects;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Says that one piece of work has to finish before another begins.
	 *
	 * <p>
	 * <strong>The order of the checks is the design.</strong> A self-edge is a fact about
	 * the request alone, so it is answered before anything is looked up — a caller who
	 * put the same identifier in both boxes learns nothing about which items exist by
	 * being told so. Everything after it needs rows, and the last of them needs the whole
	 * graph.
	 *
	 * <p>
	 * <strong>The plan is locked before the graph is read, and stays locked until the
	 * edge is written.</strong> Acyclicity is a property of every edge at once, so two
	 * callers can each read a graph their own new edge leaves acyclic and close a loop
	 * together. There is no unique index for "acyclic" and no conditional {@code UPDATE}
	 * that would serve — that makes a race on one row safe, and this is a race on a
	 * property of all of them. See {@code ProjectRepository.lockForGraphChange}.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if either end is not an item in it
	 * @throws SelfDependencyException if both ends are the same piece of work
	 * @throws CrossProjectDependencyException if the two ends are in different plans
	 * @throws DependencyAlreadyExistsException if that arrow has already been drawn
	 * @throws DependencyCycleException if it would make a plan wait for itself
	 */
	@Transactional
	public Dependency create(UUID callerId, UUID tenantId, UUID predecessorItemId, UUID successorItemId,
			BigDecimal lagHours) {
		if (predecessorItemId.equals(successorItemId)) {
			throw new SelfDependencyException();
		}
		WorkItem predecessor = this.items.get(callerId, tenantId, predecessorItemId);
		WorkItem successor = this.items.get(callerId, tenantId, successorItemId);
		UUID projectId = predecessor.getProject().getId();
		if (!projectId.equals(successor.getProject().getId())) {
			throw new CrossProjectDependencyException();
		}
		// Taken before the graph is read, so what was read is still true when the edge
		// lands. Everything below reasons about the shape of one plan, and one caller at
		// a time may.
		this.projects.lockForGraphChange(callerId, tenantId, projectId);
		if (this.dependencies.existsBetween(predecessorItemId, successorItemId)) {
			throw new DependencyAlreadyExistsException();
		}
		requireAcyclic(tenantId, projectId, predecessorItemId, successorItemId);
		return this.dependencies.save(new Dependency(predecessor, successor, lagHours, Instant.now(this.clock)));
	}

	/**
	 * Every edge in one plan, which is the half of it a forecast reads that the item list
	 * does not carry.
	 *
	 * <p>
	 * The project is fetched rather than assumed, so a plan that does not exist answers
	 * {@code project_not_found} rather than with an empty list — "no such plan" and "a
	 * plan nobody has joined up" are different answers, and only one of them is worth
	 * acting on.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<Dependency> listInProject(UUID callerId, UUID tenantId, UUID projectId) {
		this.projects.get(callerId, tenantId, projectId);
		return this.dependencies.findAllInProject(tenantId, projectId);
	}

	/**
	 * Rubs out one edge.
	 *
	 * <p>
	 * <strong>No lock, and it needs none.</strong> The invariant the lock exists to hold
	 * is that the graph stays acyclic, and removing an arrow cannot close a loop — a plan
	 * with one fewer constraint is still a plan. Two callers removing the same edge at
	 * once is the ordinary case of one succeeding and the other being told there is no
	 * such edge.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws DependencyNotFoundException if no dependency in it has that identifier
	 */
	@Transactional
	public void delete(UUID callerId, UUID tenantId, UUID dependencyId) {
		this.memberships.requireMember(callerId, tenantId);
		this.dependencies.delete(
				this.dependencies.findInTenant(dependencyId, tenantId).orElseThrow(DependencyNotFoundException::new));
	}

	/**
	 * Refuses an edge that would make the plan wait for itself, and says which loop.
	 *
	 * <p>
	 * The new arrow runs from predecessor to successor, so it closes a cycle exactly when
	 * the predecessor can <em>already</em> be reached by following existing arrows
	 * forward from the successor. One walk answers it, and the route it took is the
	 * cycle.
	 *
	 * <p>
	 * The graph is read whole rather than a neighbour at a time: a query per hop would be
	 * a query per hop with the plan locked, and 500 items to a project is the ceiling
	 * that lets one list stand in for a traversal.
	 */
	private void requireAcyclic(UUID tenantId, UUID projectId, UUID predecessorItemId, UUID successorItemId) {
		Map<UUID, List<UUID>> onwards = new HashMap<>();
		for (Dependency edge : this.dependencies.findAllInProject(tenantId, projectId)) {
			onwards.computeIfAbsent(edge.getPredecessor().getId(), (item) -> new ArrayList<>())
				.add(edge.getSuccessor().getId());
		}
		routeBetween(onwards, successorItemId, predecessorItemId).ifPresent((route) -> {
			// The loop as it would have run: the new arrow first, then the route back
			// round. Its last step is the predecessor again, which the list does not
			// repeat — it is understood to close on the item it starts with.
			List<UUID> cycle = new ArrayList<>();
			cycle.add(predecessorItemId);
			cycle.addAll(route.subList(0, route.size() - 1));
			throw new DependencyCycleException(cycle);
		});
	}

	/**
	 * The shortest run of arrows from one item to another, or nothing where there is
	 * none.
	 *
	 * <p>
	 * Breadth first rather than depth first because the point is to hand somebody a loop
	 * they can hold in their head: the shortest one is the one worth naming, and a
	 * depth-first walk would report whichever it happened to wander into.
	 */
	private static Optional<List<UUID>> routeBetween(Map<UUID, List<UUID>> onwards, UUID from, UUID to) {
		Map<UUID, UUID> arrivedFrom = new HashMap<>();
		Set<UUID> seen = new HashSet<>();
		Deque<UUID> queue = new ArrayDeque<>();
		queue.add(from);
		seen.add(from);
		while (!queue.isEmpty()) {
			UUID at = queue.poll();
			if (at.equals(to)) {
				return Optional.of(traceBack(arrivedFrom, at));
			}
			for (UUID next : onwards.getOrDefault(at, List.of())) {
				if (seen.add(next)) {
					arrivedFrom.put(next, at);
					queue.add(next);
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Walks the arrivals back to where the search began, and turns them the right way up.
	 */
	private static List<UUID> traceBack(Map<UUID, UUID> arrivedFrom, UUID to) {
		List<UUID> route = new ArrayList<>();
		for (UUID at = to; at != null; at = arrivedFrom.get(at)) {
			route.add(at);
		}
		Collections.reverse(route);
		return route;
	}

}
