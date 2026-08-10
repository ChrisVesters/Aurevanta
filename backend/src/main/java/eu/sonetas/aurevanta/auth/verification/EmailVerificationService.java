package eu.sonetas.aurevanta.auth.verification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import eu.sonetas.aurevanta.auth.problem.InvalidTokenException;
import eu.sonetas.aurevanta.mail.EmailSender;
import eu.sonetas.aurevanta.mail.EmailTemplates;
import eu.sonetas.aurevanta.token.SingleUseToken;
import eu.sonetas.aurevanta.token.SingleUseTokenService;
import eu.sonetas.aurevanta.token.TokenPurpose;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and redeems the links that prove somebody can read the address they gave. */
@Service
public class EmailVerificationService {

	/**
	 * Long enough to survive a weekend and a spam folder, short enough that a link found
	 * in an old mailbox is no longer a way in.
	 */
	static final Duration VALID_FOR = Duration.ofDays(3);

	private final UserRepository users;

	private final SingleUseTokenService tokens;

	private final EmailSender email;

	private final EmailTemplates templates;

	private final Clock clock;

	EmailVerificationService(UserRepository users, SingleUseTokenService tokens, EmailSender email,
			EmailTemplates templates, Clock clock) {
		this.users = users;
		this.tokens = tokens;
		this.email = email;
		this.templates = templates;
		this.clock = clock;
	}

	/**
	 * Issues a link and puts it in the post.
	 *
	 * <p>
	 * Sending cannot fail the caller: {@code EmailSender} logs delivery problems rather
	 * than throwing, and waits for the surrounding transaction to commit before it sends
	 * — so the token in the link is already durable by the time anyone can follow it.
	 */
	@Transactional
	public void send(User user) {
		SingleUseToken token = this.tokens.issue(user, TokenPurpose.EMAIL_VERIFICATION, VALID_FOR);
		this.email.send(this.templates.verifyEmail(user.getEmail(), user.getDisplayName(), token.value(), VALID_FOR));
	}

	/**
	 * Redeems a link, proving the address.
	 * @throws InvalidTokenException if the link is unknown, expired, already used, or was
	 * issued for something else
	 */
	@Transactional
	public void verify(String rawToken) {
		User user = this.tokens.consume(rawToken, TokenPurpose.EMAIL_VERIFICATION)
			.orElseThrow(InvalidTokenException::new);
		user.markEmailVerified(Instant.now(this.clock));
		this.users.save(user);
	}

	/**
	 * Sends another link, if there is anyone to send one to.
	 *
	 * <p>
	 * Says nothing either way. This endpoint is unauthenticated — it has to be, since the
	 * person who needs it cannot sign in — so an answer that differed for a registered
	 * address would turn it into a way to ask who has an account. An address that is
	 * unknown, or already confirmed, quietly gets nothing.
	 */
	@Transactional
	public void resend(String email) {
		this.users.findByEmailIgnoringCase(email).filter((user) -> !user.isEmailVerified()).ifPresent(this::send);
	}

}
