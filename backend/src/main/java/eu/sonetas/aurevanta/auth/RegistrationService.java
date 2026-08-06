package eu.sonetas.aurevanta.auth;

import java.time.Clock;
import java.time.Instant;

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

/** Creates a tenant and the owner who registered it. */
@Service
public class RegistrationService {

	private final TenantRepository tenants;

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	RegistrationService(TenantRepository tenants, UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.tenants = tenants;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Registers an organisation and its first user.
	 *
	 * <p>
	 * The uniqueness checks below are for the error message; the unique constraints in
	 * the database are what actually hold under two simultaneous registrations, and a
	 * violation surfaces as {@link DataIntegrityViolationException}.
	 * @return the new owner, with their tenant loaded
	 * @throws EmailAlreadyRegisteredException if the address already has an account
	 * @throws OrganisationNameUnavailableException if the derived handle is taken
	 * @throws UnusableOrganisationNameException if no handle can be derived from the name
	 */
	@Transactional
	public User register(RegistrationRequest request) {
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
		return this.users.save(new User(tenant, email, this.passwordEncoder.encode(request.password()),
				request.displayName().trim(), UserRole.OWNER, now));
	}

}
