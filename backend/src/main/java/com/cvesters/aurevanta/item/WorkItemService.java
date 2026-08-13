package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProgressDateRequiredException;
import com.cvesters.aurevanta.problem.ProgressNotApplicableException;
import com.cvesters.aurevanta.problem.ProgressOutOfOrderException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.WorkItemNotFoundException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectService;

/**
 * The work inside a plan, and everything a member may do to it.
 *
 * <p>
 * Any member may do all of it, as with projects: roles govern administration only.
 * Nothing here deletes anything either — an item archives, and from step 3 it carries
 * estimates that M8 reads years later.
 *
 * <p>
 * Two ways in, and the difference is deliberate. <strong>Creating and listing go through
 * the project</strong>, which is looked up by {@link ProjectService} and so re-checks the
 * caller's membership and the project's organisation in one place. <strong>Changing an
 * item that already exists names only the item</strong>, because from step 3 an estimate
 * and from step 5 a dependency address items directly, and a path that repeated the
 * project would be a second identifier the server had to check agreed with the first.
 */
@Service
public class WorkItemService {

	private final WorkItemRepository items;

	private final ProjectService projects;

	private final MembershipService memberships;

	private final Clock clock;

	WorkItemService(WorkItemRepository items, ProjectService projects, MembershipService memberships, Clock clock) {
		this.items = items;
		this.projects = projects;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Writes a piece of work down. The tenant comes off the project rather than off the
	 * caller's token, so the row's own copy of it cannot disagree with the plan it is in.
	 *
	 * <p>
	 * An archived project accepts items, deliberately: archiving says the plan is not
	 * being worked from, and refusing here would need a refusal nobody asked for to
	 * describe somebody tidying up an old plan they had just brought back.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional
	public WorkItem create(UUID callerId, UUID tenantId, UUID projectId, String title, String description) {
		// The entity rather than the read model: how much of the plan is estimated is not
		// a question worth two queries every time somebody types a task into it.
		Project project = this.projects.get(callerId, tenantId, projectId);
		return this.items.save(new WorkItem(project, title, description, Instant.now(this.clock)));
	}

	/**
	 * The work in one plan, the items in use or the ones put away.
	 *
	 * <p>
	 * The project is fetched rather than assumed, so a plan that does not exist answers
	 * {@code project_not_found} rather than with an empty list — "no such plan" and "a
	 * plan with nothing in it" are different answers, and only one of them is worth
	 * acting on.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<WorkItem> list(UUID callerId, UUID tenantId, UUID projectId, boolean archived) {
		this.projects.get(callerId, tenantId, projectId);
		return this.items.findAllInProject(tenantId, projectId, archived);
	}

	/**
	 * One item, if the caller may reach it.
	 *
	 * <p>
	 * Public because an estimate is <em>of</em> an item, and {@code estimate} asks this
	 * question rather than looking the row up itself: whether somebody may write against
	 * a piece of work is this package's rule, and a second copy of it would be a second
	 * chance for one of them to drift.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional(readOnly = true)
	public WorkItem get(UUID callerId, UUID tenantId, UUID itemId) {
		return item(callerId, tenantId, itemId);
	}

	/**
	 * Changes what one item is called and what is said about it.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional
	public WorkItem update(UUID callerId, UUID tenantId, UUID itemId, String title, String description) {
		WorkItem item = item(callerId, tenantId, itemId);
		item.describe(title, description);
		return item;
	}

	/**
	 * Records what has already happened to one item, so a forecast can leave it out
	 * rather than predict it again.
	 *
	 * <p>
	 * <strong>The request states the whole of it, and anything it says that its own
	 * status cannot hold is refused rather than dropped.</strong> Keeping the parts that
	 * fit and discarding the rest is what this did first, and it meant somebody could
	 * record four hours against work marked not started, watch the form close, and find
	 * the number gone with nothing said about it.
	 *
	 * <p>
	 * The other two checks make the dates evidence instead of decoration: a state that
	 * needs a date and did not get one is refused rather than stamped with the server's
	 * clock, and a completion before its start is refused because nothing downstream
	 * could tell that from a fact.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 * @throws ProgressDateRequiredException if the state claimed has no date to support
	 * it
	 * @throws ProgressNotApplicableException if it carries what that state cannot hold
	 * @throws ProgressOutOfOrderException if the work finished before it began
	 */
	@Transactional
	public WorkItem recordProgress(UUID callerId, UUID tenantId, UUID itemId, WorkItemStatus status,
			LocalDate startedOn, LocalDate completedOn, BigDecimal actualEffortHours) {
		requireConsistent(status, startedOn, completedOn, actualEffortHours);
		WorkItem item = item(callerId, tenantId, itemId);
		item.recordProgress(status, startedOn, completedOn, actualEffortHours);
		return item;
	}

	/**
	 * Every way a claim can disagree with itself, checked before the item is looked up —
	 * as the estimate ordering is, and for the same reason: these are facts about the
	 * request alone, so answering them first tells a caller nothing about which items
	 * exist.
	 */
	private static void requireConsistent(WorkItemStatus status, LocalDate startedOn, LocalDate completedOn,
			BigDecimal actualEffortHours) {
		// Deliberately not a switch: one covering every constant of an enum still
		// compiles
		// to a default nothing can reach, and an unreachable branch is a hole in the
		// coverage gate that no test can close.
		if (status == WorkItemStatus.NOT_STARTED) {
			// Nothing has happened, so there is nothing to report about it.
			if (startedOn != null || completedOn != null || actualEffortHours != null) {
				throw new ProgressNotApplicableException();
			}
			return;
		}
		if (status == WorkItemStatus.IN_PROGRESS) {
			if (startedOn == null) {
				throw new ProgressDateRequiredException();
			}
			// Effort so far is a real thing to record; a completion date is not, because
			// this is the state for work that has not been finished.
			if (completedOn != null) {
				throw new ProgressNotApplicableException();
			}
			return;
		}
		// DONE, and the only state whose two dates have to agree with each other.
		if (completedOn == null) {
			throw new ProgressDateRequiredException();
		}
		// A start is optional even here — plenty of work is ticked off by somebody who
		// never marked it as begun — but where both are given they have to agree about
		// which came first.
		if (startedOn != null && completedOn.isBefore(startedOn)) {
			throw new ProgressOutOfOrderException();
		}
	}

	/**
	 * Puts one item away, or brings it back.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional
	public WorkItem setArchived(UUID callerId, UUID tenantId, UUID itemId, boolean archived) {
		WorkItem item = item(callerId, tenantId, itemId);
		if (archived) {
			item.archive(Instant.now(this.clock));
		}
		else {
			item.unarchive();
		}
		return item;
	}

	/**
	 * The caller's standing re-read, and then the item found within their own
	 * organisation. Both halves matter: the first is what stops a token outliving a
	 * membership, and the second is what stops an identifier reaching outside the
	 * organisation that token names.
	 */
	private WorkItem item(UUID callerId, UUID tenantId, UUID itemId) {
		this.memberships.requireMember(callerId, tenantId);
		return this.items.findInTenant(itemId, tenantId).orElseThrow(WorkItemNotFoundException::new);
	}

}
