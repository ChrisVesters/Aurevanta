package com.cvesters.aurevanta.project;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.tenant.Tenant;

/**
 * The plans one organisation holds, and everything a member may do to them.
 *
 * <p>
 * <strong>Any member may do all of it</strong> — create, rename, archive, bring back.
 * Roles govern administration (invitations, members, organisation settings) and nothing
 * else, because estimation is a team activity and a per-project permission model is a
 * great deal of machinery to build before anybody has asked for it.
 *
 * <p>
 * What that costs is that destroying something becomes everybody's to do, which is why
 * nothing here destroys anything: {@link #archive} is the whole of it, and the row stays.
 *
 * <p>
 * Every method starts by re-reading the caller's membership rather than trusting the
 * tenant pinned into their token, and takes the organisation from the row that comes
 * back. There is no method here that accepts a tenant identifier from a request.
 */
@Service
public class ProjectService {

	private final ProjectRepository projects;

	private final MembershipService memberships;

	private final Clock clock;

	ProjectService(ProjectRepository projects, MembershipService memberships, Clock clock) {
		this.projects = projects;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Starts a plan.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional
	public PlannedProject create(UUID callerId, UUID tenantId, String name, String description) {
		Tenant organisation = this.memberships.requireMember(callerId, tenantId).getTenant();
		// Nothing in it yet, so its coverage is not worth two queries to discover.
		return new PlannedProject(
				this.projects.save(new Project(organisation, name, description, Instant.now(this.clock))), 0, 0);
	}

	/**
	 * The organisation's plans, the ones in use or the ones put away.
	 *
	 * <p>
	 * Archived ones are asked for rather than mixed in, because a listing that returned
	 * both would leave every caller to filter — and the one that forgot would show work
	 * somebody had deliberately put away as though it were live.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public List<PlannedProject> list(UUID callerId, UUID tenantId, boolean archived) {
		this.memberships.requireMember(callerId, tenantId);
		List<Project> found = this.projects.findAllInTenant(tenantId, archived);
		Map<UUID, Long> items = counted(this.projects.itemCounts(tenantId));
		Map<UUID, Long> estimated = counted(this.projects.estimatedItemCounts(tenantId));
		return found.stream().map((project) -> planned(project, items, estimated)).toList();
	}

	/**
	 * One plan, named by identifier — the entity, for whoever needs the row rather than
	 * the screen.
	 *
	 * <p>
	 * Kept apart from {@link #planned} because most callers are other services: an item
	 * being created needs the project it belongs to and nothing else, and counting how
	 * much of a plan is estimated every time somebody types a task into it would be two
	 * queries spent on a number nobody was asking for.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public Project get(UUID callerId, UUID tenantId, UUID projectId) {
		this.memberships.requireMember(callerId, tenantId);
		return project(projectId, tenantId);
	}

	/**
	 * The same plan, held against anybody else reasoning about its shape at the same
	 * moment.
	 *
	 * <p>
	 * Public for {@code dependency}, which is the one thing in this work whose rule is a
	 * property of a whole graph rather than of any row in it: two callers can each read a
	 * plan their own new edge leaves acyclic and close a loop together. The lock lives
	 * here rather than being taken on the repository directly so that reaching a plan
	 * still goes through the one method that re-reads the caller's membership — a lock is
	 * not a reason to skip the check that says they may be here at all.
	 *
	 * <p>
	 * The row it locks holds none of the graph. That is the point: the edges that would
	 * have to be locked instead are the ones that do not exist yet.
	 *
	 * <p>
	 * <strong>Takes the lock and answers with nothing, including when there is no such
	 * plan.</strong> Every caller has already reached a piece of work inside it, which is
	 * what says the plan exists and belongs here — so a {@code project_not_found} raised
	 * from this line would be a refusal no request could produce and no test could cover.
	 * A plan that is not there has no items and no edges either, so there is nothing for
	 * a lock over it to protect.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional
	public void lockForGraphChange(UUID callerId, UUID tenantId, UUID projectId) {
		this.memberships.requireMember(callerId, tenantId);
		this.projects.lockForGraphChange(projectId, tenantId);
	}

	/** The same plan with its coverage, which is what the API answers with. */
	@Transactional(readOnly = true)
	public PlannedProject planned(UUID callerId, UUID tenantId, UUID projectId) {
		return withCoverage(get(callerId, tenantId, projectId), tenantId);
	}

	/**
	 * Changes what a plan is called and what is said about it.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional
	public PlannedProject update(UUID callerId, UUID tenantId, UUID projectId, String name, String description) {
		this.memberships.requireMember(callerId, tenantId);
		Project project = project(projectId, tenantId);
		project.describe(name, description);
		return withCoverage(project, tenantId);
	}

	/**
	 * Puts a plan away, or brings it back.
	 *
	 * <p>
	 * One method rather than two because they are one decision read in both directions,
	 * and splitting them would be two lookups and two membership checks to keep in step.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional
	public PlannedProject setArchived(UUID callerId, UUID tenantId, UUID projectId, boolean archived) {
		this.memberships.requireMember(callerId, tenantId);
		Project project = project(projectId, tenantId);
		if (archived) {
			project.archive(Instant.now(this.clock));
		}
		else {
			project.unarchive();
		}
		return withCoverage(project, tenantId);
	}

	private Project project(UUID projectId, UUID tenantId) {
		return this.projects.findInTenant(projectId, tenantId).orElseThrow(ProjectNotFoundException::new);
	}

	private PlannedProject withCoverage(Project project, UUID tenantId) {
		return planned(project, counted(this.projects.itemCounts(tenantId)),
				counted(this.projects.estimatedItemCounts(tenantId)));
	}

	/**
	 * A plan with its two counts, each defaulting to none: a project nobody has put
	 * anything in is absent from a grouped count rather than present with a zero.
	 */
	private static PlannedProject planned(Project project, Map<UUID, Long> items, Map<UUID, Long> estimated) {
		return new PlannedProject(project, items.getOrDefault(project.getId(), 0L),
				estimated.getOrDefault(project.getId(), 0L));
	}

	private static Map<UUID, Long> counted(List<ProjectCount> rows) {
		return rows.stream().collect(Collectors.toMap(ProjectCount::projectId, ProjectCount::count));
	}

}
