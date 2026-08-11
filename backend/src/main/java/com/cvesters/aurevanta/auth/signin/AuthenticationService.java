package com.cvesters.aurevanta.auth.signin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.problem.EmailNotVerifiedException;
import com.cvesters.aurevanta.problem.InvalidCredentialsException;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;

/**
 * Verifies credentials and resolves which organisation, if any, to sign the caller into.
 */
@Service
public class AuthenticationService {

	private final UserRepository users;

	private final MembershipRepository memberships;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	/**
	 * Hash of a value nobody knows, matched against when the email is unknown so that a
	 * missing account costs the same time as a wrong password. Without it, response time
	 * alone reveals which addresses are registered.
	 */
	private final String decoyHash;

	AuthenticationService(UserRepository users, MembershipRepository memberships, PasswordEncoder passwordEncoder,
			Clock clock) {
		this.users = users;
		this.memberships = memberships;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
		this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Checks the password, then works out what the caller can be signed into.
	 *
	 * <p>
	 * One membership is the common case and resolves to a session directly. Anything else
	 * — several to pick from, or none at all — is handed back for the caller to decide,
	 * because choosing on their behalf would either be arbitrary or a lie.
	 * @throws InvalidCredentialsException if the email is unknown or the password wrong
	 */
	@Transactional
	public SignIn signIn(LoginRequest request) {
		User user = authenticate(request);
		// Only after the password has been checked. Refusing an unverified address before
		// that would answer differently for a registered address than an unknown one, and
		// turn sign-in into a way to ask who has an account.
		if (!user.isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		List<Membership> held = this.memberships.findAllForUser(user.getId());
		if (held.size() != 1) {
			return new SignIn.ChooseOrganisation(user, held);
		}
		Membership only = held.getFirst();
		only.recordAccess(Instant.now(this.clock));
		return new SignIn.IntoOrganisation(only);
	}

	private User authenticate(LoginRequest request) {
		// Already stripped by the request record, so the lookup matches what registration
		// stored rather than differing by a pasted space.
		User user = this.users.findByEmailIgnoringCase(request.email()).orElse(null);
		if (user == null) {
			this.passwordEncoder.matches(request.password(), this.decoyHash);
			throw new InvalidCredentialsException();
		}
		if (!this.passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new InvalidCredentialsException();
		}
		return user;
	}

	/**
	 * Reloads the membership an access token names, so a token outlives the standing it
	 * describes by no more than the request that presents it. A deleted account and a
	 * membership since revoked both land here.
	 * @throws InvalidCredentialsException if the caller no longer holds that membership
	 */
	@Transactional(readOnly = true)
	public Membership requireMembership(UUID userId, UUID tenantId) {
		return this.memberships.findForUserInTenant(userId, tenantId).orElseThrow(InvalidCredentialsException::new);
	}

}
