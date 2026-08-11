package com.cvesters.aurevanta.tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.InvalidCredentialsException;
import com.cvesters.aurevanta.problem.SlugTakenException;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

/**
 * Creating an organisation, with whoever asked for it as its owner.
 *
 * <p>
 * Two callers: registering, which makes the account and the organisation together, and
 * somebody who already has an account starting a second one. They were one piece of code
 * in {@code RegistrationService} until the second appeared, and the part they share is
 * the whole of it — accepting or refusing the handle, and writing the tenant with an
 * {@code OWNER} membership beside it.
 *
 * <p>
 * Nothing here derives a handle. The caller brings one, because a refusal is only worth
 * raising against something somebody chose.
 */
@Service
public class OrganisationService {

	private final TenantRepository tenants;

	private final MembershipService memberships;

	private final UserRepository users;

	private final Clock clock;

	OrganisationService(TenantRepository tenants, MembershipService memberships, UserRepository users, Clock clock) {
		this.tenants = tenants;
		this.memberships = memberships;
		this.users = users;
		this.clock = clock;
	}

	/**
	 * Starts an organisation for somebody who already has an account.
	 *
	 * <p>
	 * The way out of belonging to nothing. Losing your last membership is a state the
	 * product can produce — an owner removes you — and without this the only way back
	 * would be waiting for somebody else to invite you, which is not something a person
	 * can do for themselves.
	 * @param callerId from a verified token, identity or access; either will do, because
	 * what is being created belongs to no organisation yet
	 * @throws InvalidCredentialsException if the account behind the token is gone
	 */
	@Transactional
	public Membership create(UUID callerId, String name, String slug) {
		User owner = this.users.findById(callerId).orElseThrow(InvalidCredentialsException::new);
		Membership membership = createFor(owner, name, slug, Instant.now(this.clock));
		// They are about to be handed a session for it, which is the choice sign-in would
		// otherwise have recorded.
		membership.recordAccess(Instant.now(this.clock));
		return membership;
	}

	/**
	 * The same, for a user who is being created in the same transaction and so cannot be
	 * looked up yet.
	 *
	 * <p>
	 * The check below is what produces a refusal worth reading — one that names a free
	 * alternative. {@code uq_tenants_slug} is what actually holds when two callers get
	 * past it together, and {@code ApiExceptionHandler} reads that violation back into
	 * the same refusal, minus the alternative it can no longer go and look for.
	 * @throws SlugTakenException if somebody already has that handle
	 */
	@Transactional
	public Membership createFor(User owner, String name, String slug, Instant now) {
		if (this.tenants.existsBySlug(slug)) {
			throw new SlugTakenException(nextFree(slug));
		}
		Tenant tenant = this.tenants.save(new Tenant(name, slug, now));
		return this.memberships.join(owner, tenant, UserRole.OWNER, now);
	}

	/**
	 * The first handle counting on from this one that nobody holds.
	 *
	 * <p>
	 * Unbounded, and terminates for the reason that matters: the candidates are endless
	 * and the organisations are not. Each turn is an exact lookup against the unique
	 * index, and the number of turns is the number of organisations already counting from
	 * this handle — a handful, not a page.
	 */
	private String nextFree(String slug) {
		String base = Slug.base(slug);
		int n = 2;
		while (this.tenants.existsBySlug(Slug.withSuffix(base, n))) {
			n++;
		}
		return Slug.withSuffix(base, n);
	}

}
