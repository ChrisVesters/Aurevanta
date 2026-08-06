package eu.sonetas.aurevanta.auth;

import java.util.UUID;

import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Verifies credentials and resolves the account behind a valid access token. */
@Service
public class AuthenticationService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	/**
	 * Hash of a value nobody knows, matched against when the email is unknown so that a
	 * missing account costs the same time as a wrong password. Without it, response time
	 * alone reveals which addresses are registered.
	 */
	private final String decoyHash;

	AuthenticationService(UserRepository users, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * @return the authenticated user, with their tenant loaded
	 * @throws InvalidCredentialsException if the email is unknown or the password wrong
	 */
	@Transactional(readOnly = true)
	public User authenticate(LoginRequest request) {
		User user = this.users.findWithTenantByEmailIgnoringCase(request.email().trim()).orElse(null);
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
	 * Reloads the account named by a token, so a token outlives its account by no more
	 * than the request that presents it.
	 * @throws InvalidCredentialsException if the account no longer exists
	 */
	@Transactional(readOnly = true)
	public User requireAccount(UUID userId) {
		return this.users.findWithTenantById(userId).orElseThrow(InvalidCredentialsException::new);
	}

}
