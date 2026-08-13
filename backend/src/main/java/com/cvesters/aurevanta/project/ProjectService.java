package com.cvesters.aurevanta.project;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
	public Project create(UUID callerId, UUID tenantId, String name, String description) {
		Tenant organisation = this.memberships.requireMember(callerId, tenantId).getTenant();
		return this.projects.save(new Project(organisation, name, description, Instant.now(this.clock)));
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
	public List<Project> list(UUID callerId, UUID tenantId, boolean archived) {
		this.memberships.requireMember(callerId, tenantId);
		return this.projects.findAllInTenant(tenantId, archived);
	}

	/**
	 * One plan, named by identifier.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public Project get(UUID callerId, UUID tenantId, UUID projectId) {
		this.memberships.requireMember(callerId, tenantId);
		return project(projectId, tenantId);
	}

	/**
	 * Changes what a plan is called and what is said about it.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional
	public Project update(UUID callerId, UUID tenantId, UUID projectId, String name, String description) {
		this.memberships.requireMember(callerId, tenantId);
		Project project = project(projectId, tenantId);
		project.describe(name, description);
		return project;
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
	public Project setArchived(UUID callerId, UUID tenantId, UUID projectId, boolean archived) {
		this.memberships.requireMember(callerId, tenantId);
		Project project = project(projectId, tenantId);
		if (archived) {
			project.archive(Instant.now(this.clock));
		}
		else {
			project.unarchive();
		}
		return project;
	}

	private Project project(UUID projectId, UUID tenantId) {
		return this.projects.findInTenant(projectId, tenantId).orElseThrow(ProjectNotFoundException::new);
	}

}
