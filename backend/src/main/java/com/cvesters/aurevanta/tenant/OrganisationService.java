package com.cvesters.aurevanta.tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.InvalidCredentialsException;
import com.cvesters.aurevanta.problem.OrganisationNameUnavailableException;
import com.cvesters.aurevanta.problem.UnusableOrganisationNameException;
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
 * the whole of it — deriving the handle, refusing a name that yields none or one already
 * taken, and writing the tenant with an {@code OWNER} membership beside it.
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
	public Membership create(UUID callerId, String name) {
		User owner = this.users.findById(callerId).orElseThrow(InvalidCredentialsException::new);
		Membership membership = createFor(owner, name, Instant.now(this.clock));
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
	 * The uniqueness check below is for the error message; the unique constraint on the
	 * handle is what actually holds under two simultaneous requests, and a violation
	 * surfaces as a {@code DataIntegrityViolationException} reported the same way.
	 * @throws UnusableOrganisationNameException if no handle can be derived from the name
	 * @throws OrganisationNameUnavailableException if the derived handle is taken
	 */
	@Transactional
	public Membership createFor(User owner, String name, Instant now) {
		String slug = Slug.of(name);
		if (slug.isEmpty()) {
			throw new UnusableOrganisationNameException();
		}
		if (this.tenants.existsBySlug(slug)) {
			throw new OrganisationNameUnavailableException();
		}
		Tenant tenant = this.tenants.save(new Tenant(name, slug, now));
		return this.memberships.join(owner, tenant, UserRole.OWNER, now);
	}

}
