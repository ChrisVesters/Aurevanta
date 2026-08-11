package com.cvesters.aurevanta.auth.registration;

import java.time.Clock;
import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.problem.EmailAlreadyRegisteredException;
import com.cvesters.aurevanta.auth.verification.EmailVerificationService;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.tenant.OrganisationService;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;

/** Creates a tenant, the person who registered it, and the membership that ties them. */
@Service
public class RegistrationService {

	private final UserRepository users;

	private final OrganisationService organisations;

	private final PasswordEncoder passwordEncoder;

	private final EmailVerificationService verification;

	private final Clock clock;

	RegistrationService(UserRepository users, OrganisationService organisations, PasswordEncoder passwordEncoder,
			EmailVerificationService verification, Clock clock) {
		this.users = users;
		this.organisations = organisations;
		this.passwordEncoder = passwordEncoder;
		this.verification = verification;
		this.clock = clock;
	}

	/**
	 * Registers an organisation and its first user, in one transaction so an account is
	 * never left belonging to nothing.
	 *
	 * <p>
	 * The uniqueness check below is for the error message; the unique constraints in the
	 * database are what actually hold under two simultaneous registrations, and a
	 * violation surfaces as {@link DataIntegrityViolationException}.
	 *
	 * <p>
	 * The organisation itself is made by {@code OrganisationService}, which also makes
	 * one for somebody who already has an account. What the two do is the same thing, and
	 * a second copy of it here would be a second place for the rules about a name to
	 * drift.
	 * @return the owner's membership, carrying both the new user and the new organisation
	 * @throws EmailAlreadyRegisteredException if the address already has an account
	 */
	@Transactional
	public Membership register(RegistrationRequest request) {
		// Already stripped by the request record, so that validation saw the same text
		// that is about to be stored.
		String email = request.email();
		if (this.users.existsByEmailIgnoringCase(email)) {
			throw new EmailAlreadyRegisteredException();
		}

		Instant now = Instant.now(this.clock);
		User user = this.users
			.save(new User(email, this.passwordEncoder.encode(request.password()), request.displayName(), now));
		Membership owner = this.organisations.createFor(user, request.organisationName(), request.organisationSlug(),
				now);

		// The account exists but cannot be used until this link is followed. Sending
		// cannot fail registration — delivery problems are logged, not thrown — and the
		// message waits for this transaction to commit, so the token is durable before
		// anybody can follow it.
		this.verification.send(user);
		return owner;
	}

}
