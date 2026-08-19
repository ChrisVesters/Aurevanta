package com.cvesters.aurevanta.resource;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.PersonNotAMemberException;
import com.cvesters.aurevanta.problem.ResourceNotFoundException;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.user.User;

/**
 * The pools one organisation says it has, and everything a member may do to them.
 *
 * <p>
 * <strong>Any member may do all of it</strong>, like everything else about a plan: roles
 * govern administration and nothing else. Describing a team is planning, and a per-pool
 * permission model would be a great deal of machinery in front of a screen whose whole
 * content is "we have three backend engineers".
 *
 * <p>
 * <strong>Nothing here destroys anything.</strong> {@link #setArchived} is the whole of
 * it, and the row stays — because a forecast stored the declaration it was scheduled
 * under, and a pool that had vanished would leave that snapshot describing an identifier.
 *
 * <p>
 * Every method re-reads the caller's membership rather than trusting the tenant pinned
 * into a token up to twelve hours ago, and takes the organisation from the row that comes
 * back.
 */
@Service
public class ResourceService {

	private final ResourceRepository resources;

	private final MembershipService memberships;

	private final Clock clock;

	ResourceService(ResourceRepository resources, MembershipService memberships, Clock clock) {
		this.resources = resources;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Declares a pool.
	 * @param personId optionally who this pool is, which must be somebody in this
	 * organisation at the moment it is said
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws PersonNotAMemberException if the named person does not
	 */
	@Transactional
	public Resource create(UUID callerId, UUID tenantId, String name, int units, UUID personId) {
		Tenant organisation = this.memberships.requireMember(callerId, tenantId).getTenant();
		return this.resources
			.save(new Resource(organisation, name, units, person(personId, tenantId), Instant.now(this.clock)));
	}

	/**
	 * The organisation's pools, the ones in use or the ones put away.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public List<Resource> list(UUID callerId, UUID tenantId, boolean archived) {
		this.memberships.requireMember(callerId, tenantId);
		return this.resources.findAllInTenant(tenantId, archived);
	}

	/**
	 * One pool, named by identifier.
	 *
	 * <p>
	 * Public because {@code requirement} reaches it: what an item needs is checked
	 * against the pools through this method rather than against the table, so the
	 * membership rule is the same rule reached rather than a second copy of it.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ResourceNotFoundException if no pool in it has that identifier
	 */
	@Transactional(readOnly = true)
	public Resource get(UUID callerId, UUID tenantId, UUID resourceId) {
		this.memberships.requireMember(callerId, tenantId);
		return resource(resourceId, tenantId);
	}

	/**
	 * Changes what a pool is called, how many of it there are, and who it is.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ResourceNotFoundException if no pool in it has that identifier
	 * @throws PersonNotAMemberException if the named person is not in this organisation
	 */
	@Transactional
	public Resource update(UUID callerId, UUID tenantId, UUID resourceId, String name, int units, UUID personId) {
		this.memberships.requireMember(callerId, tenantId);
		Resource resource = resource(resourceId, tenantId);
		resource.describe(name, units, person(personId, tenantId));
		return resource;
	}

	/**
	 * Puts a pool away, or brings it back.
	 *
	 * <p>
	 * One method with a boolean rather than two, for {@code ProjectService.setArchived}'s
	 * reason: they are one decision read in both directions, and two would be two lookups
	 * and two membership checks to keep in step.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ResourceNotFoundException if no pool in it has that identifier
	 */
	@Transactional
	public Resource setArchived(UUID callerId, UUID tenantId, UUID resourceId, boolean archived) {
		this.memberships.requireMember(callerId, tenantId);
		Resource resource = resource(resourceId, tenantId);
		if (archived) {
			resource.archive(Instant.now(this.clock));
		}
		else {
			resource.unarchive();
		}
		return resource;
	}

	private Resource resource(UUID resourceId, UUID tenantId) {
		return this.resources.findInTenant(resourceId, tenantId).orElseThrow(ResourceNotFoundException::new);
	}

	/**
	 * Whoever a pool is said to be, checked against this organisation at the moment it is
	 * said.
	 *
	 * <p>
	 * <strong>Checked on the way in and never on the way out</strong>, which is the
	 * estimator rule exactly: a pool named after somebody who has since left keeps naming
	 * them, because it records what a team was rather than who may sign in today.
	 * Refusing a stranger here is not a disclosure — a member can already list their
	 * colleagues — and it is what stops a pool pointing at an account in another
	 * organisation.
	 */
	private User person(UUID personId, UUID tenantId) {
		if (personId == null) {
			return null;
		}
		return this.memberships.memberOf(personId, tenantId).orElseThrow(PersonNotAMemberException::new).getUser();
	}

}
