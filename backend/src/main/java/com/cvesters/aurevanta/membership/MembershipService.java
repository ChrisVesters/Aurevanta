package com.cvesters.aurevanta.membership;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.problem.LastOwnerException;
import com.cvesters.aurevanta.problem.MemberNotFoundException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NotAnOwnerException;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRole;

/** Who belongs to which organisation, in what standing, and who may change that. */
@Service
public class MembershipService {

	private final MembershipRepository memberships;

	private final Clock clock;

	MembershipService(MembershipRepository memberships, Clock clock) {
		this.memberships = memberships;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Membership> forUser(UUID userId) {
		return this.memberships.findAllForUser(userId);
	}

	/**
	 * Finds the membership a caller is asking to act under and records the choice.
	 *
	 * <p>
	 * The lookup is by user <em>and</em> tenant together, so a tenant identifier supplied
	 * in the request cannot widen the caller's reach — it only picks among organisations
	 * they already belong to. An empty result means exactly that: not a member.
	 */
	@Transactional
	public Optional<Membership> select(UUID userId, UUID tenantId) {
		Optional<Membership> chosen = this.memberships.findForUserInTenant(userId, tenantId);
		chosen.ifPresent((membership) -> membership.recordAccess(Instant.now(this.clock)));
		return chosen;
	}

	/**
	 * Puts somebody into an organisation. The one place a membership is created, so that
	 * registering, accepting an invitation and starting a second organisation cannot come
	 * to disagree about what joining one means.
	 *
	 * <p>
	 * Says nothing about whether they may: who is allowed to hand out a membership
	 * differs by route — an invitation they hold, an owner acting, or the fact that they
	 * just made the organisation — and each of those is checked by whoever knows about
	 * it.
	 */
	@Transactional
	public Membership join(User user, Tenant tenant, UserRole role, Instant joinedAt) {
		return this.memberships.save(new Membership(user, tenant, role, joinedAt));
	}

	/** Whether somebody already belongs to one organisation, however they got there. */
	@Transactional(readOnly = true)
	public boolean hasMember(UUID userId, UUID tenantId) {
		return this.memberships.findForUserInTenant(userId, tenantId).isPresent();
	}

	/**
	 * The same question asked of an address rather than an account, which is what
	 * inviting somebody has to ask: at that point there may be no account to name.
	 */
	@Transactional(readOnly = true)
	public boolean hasMemberWithEmail(UUID tenantId, String email) {
		return this.memberships.existsInTenantByEmailIgnoringCase(tenantId, email);
	}

	/**
	 * Everybody in the caller's organisation, which any member of it may see. Colleagues
	 * knowing who their colleagues are is the point of the list; what they may
	 * <em>do</em> about it is decided per operation below.
	 */
	@Transactional(readOnly = true)
	public List<Membership> membersOf(UUID callerId, UUID tenantId) {
		requireMember(callerId, tenantId);
		return this.memberships.findAllInTenant(tenantId);
	}

	/**
	 * Changes what one person may do in the caller's organisation.
	 *
	 * <p>
	 * Nothing withdraws a token already issued, so somebody demoted a minute ago keeps
	 * the role pinned into theirs until it expires — up to twelve hours. Every endpoint
	 * that cares reads the membership back rather than trusting the claim, which is what
	 * makes that survivable; a shorter answer would need a token version on the user or a
	 * deny list, and neither is in M1.
	 * @throws MemberNotFoundException if nobody in the caller's own organisation has that
	 * identifier
	 * @throws LastOwnerException if it would leave the organisation with no owner
	 */
	@Transactional
	public Membership changeRole(UUID callerId, UUID tenantId, UUID membershipId, UserRole role) {
		requireOwner(callerId, tenantId);
		Membership member = member(membershipId, tenantId);
		if (role != UserRole.OWNER) {
			requireAnotherOwnerRemains(member, tenantId);
		}
		member.changeRole(role);
		return member;
	}

	/**
	 * Takes somebody out of the caller's organisation.
	 *
	 * <p>
	 * Deletes the membership and never the identity: the person keeps their account,
	 * their password and every other organisation they belong to. Losing their
	 * <em>last</em> membership leaves them holding an account with nothing in it, which
	 * is a state the product has an answer for rather than a deleted account, which it
	 * does not.
	 *
	 * <p>
	 * An owner may remove themselves, provided another remains — the rule is about the
	 * organisation keeping an administrator, not about who is asking.
	 * @throws MemberNotFoundException if nobody in the caller's own organisation has that
	 * identifier
	 * @throws LastOwnerException if it would leave the organisation with no owner
	 */
	@Transactional
	public void remove(UUID callerId, UUID tenantId, UUID membershipId) {
		requireOwner(callerId, tenantId);
		Membership member = member(membershipId, tenantId);
		requireAnotherOwnerRemains(member, tenantId);
		this.memberships.delete(member);
	}

	/**
	 * The caller's standing, read from the database rather than taken from the claims in
	 * their token. Those claims were true when the token was issued, and an access token
	 * lasts twelve hours: somebody demoted or removed in between would otherwise go on
	 * administering an organisation they no longer belong to, for the rest of the day.
	 *
	 * <p>
	 * Public because it is the one rule every owner-only endpoint shares, in this package
	 * and in {@code invitation}. Two copies of it would be two chances for one to drift,
	 * on the question of who may administer an organisation.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws NotAnOwnerException if they belong to it but may not administer it
	 */
	@Transactional(readOnly = true)
	public Membership requireOwner(UUID callerId, UUID tenantId) {
		Membership caller = requireMember(callerId, tenantId);
		if (caller.getRole() != UserRole.OWNER) {
			throw new NotAnOwnerException();
		}
		return caller;
	}

	/**
	 * The same re-read for an endpoint that any member may reach, which since M2 is most
	 * of them: plan data is written by everybody, and roles govern administration only.
	 *
	 * <p>
	 * Public for the same reason {@link #requireOwner} is. The tenant in an access token
	 * was true when it was issued and stays there for twelve hours, so somebody removed
	 * this morning would otherwise go on reading and writing an organisation's plans for
	 * the rest of the day — and a second copy of that check somewhere else would be a
	 * second chance for one of them to drift.
	 * @return the caller's membership, whose {@code getTenant()} is the organisation
	 * everything they may touch belongs to — so callers take the tenant from a row that
	 * proved they belong to it rather than from the request
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public Membership requireMember(UUID callerId, UUID tenantId) {
		return this.memberships.findForUserInTenant(callerId, tenantId).orElseThrow(NotAMemberException::new);
	}

	private Membership member(UUID membershipId, UUID tenantId) {
		return this.memberships.findInTenant(membershipId, tenantId).orElseThrow(MemberNotFoundException::new);
	}

	/**
	 * Refuses to take the last owner away.
	 *
	 * <p>
	 * The count is taken under a lock, and has to be: two owners removing each other at
	 * the same moment would each count two and each conclude one remains. Everything else
	 * here is recoverable by an owner; that is not recoverable by anybody.
	 */
	private void requireAnotherOwnerRemains(Membership member, UUID tenantId) {
		if (member.getRole() != UserRole.OWNER) {
			return;
		}
		if (this.memberships.lockOwners(tenantId).size() <= 1) {
			throw new LastOwnerException();
		}
	}

}
