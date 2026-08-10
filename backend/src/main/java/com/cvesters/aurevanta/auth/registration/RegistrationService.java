package com.cvesters.aurevanta.auth.registration;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.auth.problem.EmailAlreadyRegisteredException;
import com.cvesters.aurevanta.auth.problem.OrganisationNameUnavailableException;
import com.cvesters.aurevanta.auth.problem.UnusableOrganisationNameException;
import com.cvesters.aurevanta.auth.verification.EmailVerificationService;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.tenant.Slug;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

/** Creates a tenant, the person who registered it, and the membership that ties them. */
@Service
public class RegistrationService {

	private final TenantRepository tenants;

	private final UserRepository users;

	private final MembershipRepository memberships;

	private final PasswordEncoder passwordEncoder;

	private final EmailVerificationService verification;

	private final Clock clock;

	RegistrationService(TenantRepository tenants, UserRepository users, MembershipRepository memberships,
			PasswordEncoder passwordEncoder, EmailVerificationService verification, Clock clock) {
		this.tenants = tenants;
		this.users = users;
		this.memberships = memberships;
		this.passwordEncoder = passwordEncoder;
		this.verification = verification;
		this.clock = clock;
	}

	/**
	 * Registers an organisation and its first user, in one transaction so an account is
	 * never left belonging to nothing.
	 *
	 * <p>
	 * The uniqueness checks below are for the error message; the unique constraints in
	 * the database are what actually hold under two simultaneous registrations, and a
	 * violation surfaces as {@link DataIntegrityViolationException}.
	 * @return the owner's membership, carrying both the new user and the new organisation
	 * @throws EmailAlreadyRegisteredException if the address already has an account
	 * @throws OrganisationNameUnavailableException if the derived handle is taken
	 * @throws UnusableOrganisationNameException if no handle can be derived from the name
	 */
	@Transactional
	public Membership register(RegistrationRequest request) {
		// Already stripped by the request record, so that validation saw the same text
		// that is about to be stored.
		String email = request.email();
		String organisationName = request.organisationName();
		String slug = Slug.of(organisationName);
		if (slug.isEmpty()) {
			throw new UnusableOrganisationNameException();
		}
		if (this.users.existsByEmailIgnoringCase(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		if (this.tenants.existsBySlug(slug)) {
			throw new OrganisationNameUnavailableException();
		}

		Instant now = Instant.now(this.clock);
		Tenant tenant = this.tenants.save(new Tenant(organisationName, slug, now));
		User user = this.users
			.save(new User(email, this.passwordEncoder.encode(request.password()), request.displayName(), now));
		Membership owner = this.memberships.save(new Membership(user, tenant, UserRole.OWNER, now));

		// The account exists but cannot be used until this link is followed. Sending
		// cannot fail registration — delivery problems are logged, not thrown — and the
		// message waits for this transaction to commit, so the token is durable before
		// anybody can follow it.
		this.verification.send(user);
		return owner;
	}

}
