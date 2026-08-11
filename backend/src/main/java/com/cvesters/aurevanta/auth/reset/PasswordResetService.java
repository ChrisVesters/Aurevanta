package com.cvesters.aurevanta.auth.reset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.problem.InvalidTokenException;
import com.cvesters.aurevanta.mail.EmailSender;
import com.cvesters.aurevanta.mail.EmailTemplates;
import com.cvesters.aurevanta.token.SingleUseToken;
import com.cvesters.aurevanta.token.SingleUseTokenService;
import com.cvesters.aurevanta.token.TokenPurpose;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;

/**
 * Issues and redeems the links that let somebody choose a new password without knowing
 * the old one.
 *
 * <p>
 * Under the verification gate this is more than a convenience. An account whose
 * confirmation message never arrived cannot sign in and cannot be recovered by signing in
 * — so this is the only way back, and it works because following a reset link proves
 * control of the address exactly as a confirmation link does.
 */
@Service
public class PasswordResetService {

	/**
	 * Deliberately shorter than a confirmation link's three days. This is the strongest
	 * thing the application puts in an inbox: whoever holds it holds the account, without
	 * needing the password. An hour is enough to read a message and act on it, and little
	 * enough that a mailbox read by somebody else later is not a way in.
	 */
	static final Duration VALID_FOR = Duration.ofHours(1);

	private final UserRepository users;

	private final SingleUseTokenService tokens;

	private final EmailSender email;

	private final EmailTemplates templates;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	PasswordResetService(UserRepository users, SingleUseTokenService tokens, EmailSender email,
			EmailTemplates templates, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.email = email;
		this.templates = templates;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Sends a link to whoever holds this address, if anybody does.
	 *
	 * <p>
	 * Says nothing either way. The endpoint is unauthenticated — it has to be, since
	 * somebody who has lost their password cannot sign in to ask — so an answer that
	 * differed for a registered address would turn it into a way to ask who has an
	 * account. An unknown address quietly gets nothing.
	 *
	 * <p>
	 * An unconfirmed address is deliberately <em>not</em> excluded, unlike the
	 * confirmation resend. The person whose first message went astray is precisely who
	 * needs this, and the link proves the address just as well.
	 */
	@Transactional
	public void request(String email) {
		this.users.findByEmailIgnoringCase(email).ifPresent(this::send);
	}

	private void send(User user) {
		SingleUseToken token = this.tokens.issue(user, TokenPurpose.PASSWORD_RESET, VALID_FOR);
		this.email.send(this.templates.resetPassword(user.getEmail(), user.getDisplayName(), token.value(), VALID_FOR));
	}

	/**
	 * Redeems a link and puts the new password behind it.
	 * @throws InvalidTokenException if the link is unknown, expired, already used, or was
	 * issued for something else — a confirmation link, in particular, cannot be spent
	 * here
	 */
	@Transactional
	public void confirm(String rawToken, String newPassword) {
		User user = this.tokens.consume(rawToken, TokenPurpose.PASSWORD_RESET).orElseThrow(InvalidTokenException::new);
		user.changePassword(this.passwordEncoder.encode(newPassword));
		// Following the link proved the address, which is all a confirmation link proves.
		// So recovering an account confirms it, and nobody is left holding one they can
		// neither sign into nor recover. Keeps the original moment if there was one.
		user.markEmailVerified(Instant.now(this.clock));
		this.users.save(user);
		// Every other reset link this account holds dies with it; see revokeAll.
		this.tokens.revokeAll(user, TokenPurpose.PASSWORD_RESET);
	}

}
