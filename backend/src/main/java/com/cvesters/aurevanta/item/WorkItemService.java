package com.cvesters.aurevanta.item;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.NotAMemberException;
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
