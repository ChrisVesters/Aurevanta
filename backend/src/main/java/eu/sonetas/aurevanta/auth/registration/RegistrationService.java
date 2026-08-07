package eu.sonetas.aurevanta.auth.registration;

import java.time.Clock;
import java.time.Instant;

import eu.sonetas.aurevanta.auth.problem.EmailAlreadyRegisteredException;
import eu.sonetas.aurevanta.auth.problem.OrganisationNameUnavailableException;
import eu.sonetas.aurevanta.auth.problem.UnusableOrganisationNameException;
import eu.sonetas.aurevanta.membership.Membership;
import eu.sonetas.aurevanta.membership.MembershipRepository;
import eu.sonetas.aurevanta.tenant.Slug;
import eu.sonetas.aurevanta.tenant.Tenant;
import eu.sonetas.aurevanta.tenant.TenantRepository;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRepository;
import eu.sonetas.aurevanta.user.UserRole;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a tenant, the person who registered it, and the membership that ties them. */
@Service
public class RegistrationService {

	private final TenantRepository tenants;

	private final UserRepository users;

	private final MembershipRepository memberships;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	RegistrationService(TenantRepository tenants, UserRepository users, MembershipRepository memberships,
			PasswordEncoder passwordEncoder, Clock clock) {
		this.tenants = tenants;
		this.users = users;
		this.memberships = memberships;
		this.passwordEncoder = passwordEncoder;
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
		String email = request.email().trim();
		String organisationName = request.organisationName().trim();
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
			.save(new User(email, this.passwordEncoder.encode(request.password()), request.displayName().trim(), now));
		return this.memberships.save(new Membership(user, tenant, UserRole.OWNER, now));
	}

}
