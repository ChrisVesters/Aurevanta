package com.cvesters.aurevanta.invitation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.mail.EmailSender;
import com.cvesters.aurevanta.mail.EmailTemplates;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.AlreadyAMemberException;
import com.cvesters.aurevanta.problem.CredentialsRequiredException;
import com.cvesters.aurevanta.problem.EmailNotVerifiedException;
import com.cvesters.aurevanta.problem.InvalidCredentialsException;
import com.cvesters.aurevanta.problem.InvalidTokenException;
import com.cvesters.aurevanta.problem.InvitationAlreadyPendingException;
import com.cvesters.aurevanta.problem.InvitationExpiredException;
import com.cvesters.aurevanta.problem.InvitationForAnotherAddressException;
import com.cvesters.aurevanta.problem.InvitationNotFoundException;
import com.cvesters.aurevanta.problem.InvitationRevokedException;
import com.cvesters.aurevanta.problem.SignInRequiredException;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.token.LinkTokens;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

/**
 * The second way into an organisation: an offer sent to an address, and its redemption.
 */
@Service
public class InvitationService {

	/**
	 * Long enough that a message found after a holiday is still worth acting on, short
	 * enough that a link in an old mailbox is not a standing way into an organisation the
	 * recipient may by then have no business joining.
	 */
	static final Duration VALID_FOR = Duration.ofDays(7);

	private final InvitationRepository invitations;

	private final MembershipService memberships;

	private final UserRepository users;

	private final EmailSender email;

	private final EmailTemplates templates;

	private final MailRateLimiter rateLimiter;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	InvitationService(InvitationRepository invitations, MembershipService memberships, UserRepository users,
			EmailSender email, EmailTemplates templates, MailRateLimiter rateLimiter, PasswordEncoder passwordEncoder,
			Clock clock) {
		this.invitations = invitations;
		this.memberships = memberships;
		this.users = users;
		this.email = email;
		this.templates = templates;
		this.rateLimiter = rateLimiter;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	/**
	 * Invites {@code email} to join {@code tenantId} as {@code role}, and sends the link.
	 * @param inviterId the caller, from their verified token and never from the request
	 * @param tenantId the organisation their token is scoped to, likewise
	 * @throws AlreadyAMemberException if the address is already in this organisation
	 * @throws InvitationAlreadyPendingException if a live invitation is already out
	 */
	@Transactional
	public Invitation invite(UUID inviterId, UUID tenantId, String email, UserRole role) {
		Membership inviter = requireSendingOwner(inviterId, tenantId);
		if (this.memberships.hasMemberWithEmail(tenantId, email)) {
			throw new AlreadyAMemberException();
		}

		Instant now = Instant.now(this.clock);
		String rawToken = LinkTokens.generate();
		Invitation invitation = write(inviter, email, role, rawToken, now);
		send(invitation, inviter, rawToken);
		return invitation;
	}

	/**
	 * Writes the invitation, reusing the row if one is already sitting in that slot.
	 *
	 * <p>
	 * A live invitation is refused rather than duplicated: two links to one organisation
	 * in one inbox is two ways in where the owner believes there is one. An expired one
	 * is renewed in place instead, because it is no longer a way in but it does still
	 * hold the slot {@code uq_invitations_pending} reserves — inserting alongside it
	 * would trip the constraint over an invitation that has already stopped working.
	 */
	private Invitation write(Membership inviter, String email, UserRole role, String rawToken, Instant now) {
		Instant expiresAt = now.plus(VALID_FOR);
		String tokenHash = LinkTokens.hash(rawToken);
		Invitation outstanding = this.invitations.findPending(inviter.getTenant().getId(), email).orElse(null);
		if (outstanding == null) {
			return this.invitations
				.save(new Invitation(inviter.getTenant(), email, role, inviter.getUser(), tokenHash, expiresAt, now));
		}
		if (!outstanding.hasExpired(now)) {
			throw new InvitationAlreadyPendingException();
		}
		outstanding.renew(role, inviter.getUser(), tokenHash, expiresAt, now);
		return this.invitations.save(outstanding);
	}

	/** Everything still outstanding in the caller's organisation. */
	@Transactional(readOnly = true)
	public List<Invitation> pending(UUID callerId, UUID tenantId) {
		this.memberships.requireOwner(callerId, tenantId);
		return this.invitations.findPendingForTenant(tenantId);
	}

	/**
	 * Withdraws an invitation before anybody acts on it, freeing the address to be
	 * invited again.
	 * @throws InvitationNotFoundException if nothing outstanding in the caller's own
	 * organisation has that identifier
	 */
	@Transactional
	public void revoke(UUID callerId, UUID tenantId, UUID invitationId) {
		this.memberships.requireOwner(callerId, tenantId);
		outstanding(invitationId, tenantId).revoke();
	}

	/**
	 * Sends the invitation again, behind a link that has not been seen before.
	 *
	 * <p>
	 * A fresh token rather than the same one twice: the point of resending is usually
	 * that the first message went astray, and a message that went astray is a message
	 * somebody else may be holding. The previous link stops working the moment this one
	 * is written.
	 * @param sourceAddress where the request came from, for the mail limit
	 */
	@Transactional
	public Invitation resend(UUID callerId, UUID tenantId, UUID invitationId, String sourceAddress) {
		Membership caller = requireSendingOwner(callerId, tenantId);
		Invitation invitation = outstanding(invitationId, tenantId);
		// Claimed after the lookup rather than before it. On an unauthenticated sender
		// the limit has to count requests, so that its own refusals cannot be used to
		// ask which addresses exist; here the recipient is named by a row the caller can
		// already see rather than by anything they typed. What matters is that the
		// recipient's budget is spent before the message is built.
		this.rateLimiter.claim(sourceAddress, invitation.getEmail());

		Instant now = Instant.now(this.clock);
		String rawToken = LinkTokens.generate();
		// The resender becomes the inviter of record, because their name is what the
		// message carries — the row and the inbox must not disagree about who is asking.
		// The role and the date it was first sent are left alone: this is the same
		// invitation, reaching the same person by a link that works.
		invitation.reissue(caller.getUser(), LinkTokens.hash(rawToken), now.plus(VALID_FOR));
		send(invitation, caller, rawToken);
		return invitation;
	}

	/**
	 * What the invitation offers, for somebody holding the link and deciding whether to
	 * act on it.
	 *
	 * <p>
	 * The address is looked up for the same reason {@link #accept} looks it up: which of
	 * the two ways through applies is decided by the address and not by whoever is
	 * holding the link. Answering it here as well only moves that decision in front of
	 * the attempt instead of behind it — see {@link InvitationPreview} for what saying so
	 * costs.
	 * @throws InvalidTokenException if no invitation was ever issued for that link, or it
	 * has already been accepted
	 * @throws InvitationExpiredException if it ran out of time
	 * @throws InvitationRevokedException if it was withdrawn
	 */
	@Transactional(readOnly = true)
	public InvitationPreview preview(String rawToken) {
		Invitation invitation = live(rawToken);
		return InvitationPreview.of(invitation, this.users.findByEmailIgnoringCase(invitation.getEmail()).isPresent());
	}

	/**
	 * Turns an invitation into a membership, creating the account if there is not one
	 * yet.
	 *
	 * <p>
	 * Two ways through, and which one applies is decided by the address rather than by
	 * the caller: nobody holds it yet, so an account is made and the person is signed in
	 * immediately — following the link proved the address, so there is nothing left to
	 * confirm; or somebody does, in which case they have to <em>be</em> that somebody.
	 * The token proves control of a mailbox and not ownership of the account registered
	 * with it, and the difference between those two is a way into somebody else's
	 * account.
	 *
	 * <p>
	 * Nothing is written until every refusal has been ruled out. A rejected attempt must
	 * leave the invitation exactly as it was, or being told to sign in first would cost
	 * the visitor the very link they were told to come back with.
	 * @param callerId whoever is signed in, or null for a visitor who is not
	 * @param credentials the account to create, needed only when nobody holds the address
	 * @return the new membership, which the caller is handed a session for
	 */
	@Transactional
	public Membership accept(String rawToken, UUID callerId, AcceptInvitationRequest credentials) {
		Invitation invitation = live(rawToken);
		User identity = (callerId != null) ? signedInInvitee(callerId, invitation) : unclaimedAddress(invitation);
		if (identity != null && this.memberships.hasMember(identity.getId(), invitation.getTenant().getId())) {
			throw new AlreadyAMemberException();
		}
		if (identity == null && credentials == null) {
			throw new CredentialsRequiredException();
		}

		Instant now = Instant.now(this.clock);
		// Spent first, and conditionally, so that two clicks arriving together cannot
		// both get a membership out of the one invitation.
		if (this.invitations.markAccepted(invitation.getId(), now) == 0) {
			throw new InvalidTokenException();
		}
		User user = (identity != null) ? identity : register(invitation, credentials, now);
		Membership membership = this.memberships.join(user, invitation.getTenant(), invitation.getRole(), now);
		// They are about to be handed a session for it, which is the choice sign-in would
		// otherwise have recorded.
		membership.recordAccess(now);
		return membership;
	}

	/**
	 * The signed-in caller, provided the invitation was addressed to them.
	 * @throws InvalidCredentialsException if the account behind the token is gone
	 * @throws InvitationForAnotherAddressException if they are somebody else
	 */
	private User signedInInvitee(UUID callerId, Invitation invitation) {
		User caller = this.users.findById(callerId).orElseThrow(InvalidCredentialsException::new);
		if (!caller.getEmail().equalsIgnoreCase(invitation.getEmail())) {
			throw new InvitationForAnotherAddressException();
		}
		return caller;
	}

	/**
	 * Confirms that nobody holds the invited address, and so that an account may be made
	 * for it.
	 * @return null, always — the absence of an identity is the answer
	 * @throws SignInRequiredException if the address already has an account
	 */
	private User unclaimedAddress(Invitation invitation) {
		if (this.users.findByEmailIgnoringCase(invitation.getEmail()).isPresent()) {
			throw new SignInRequiredException();
		}
		return null;
	}

	private User register(Invitation invitation, AcceptInvitationRequest credentials, Instant now) {
		User user = new User(invitation.getEmail(), this.passwordEncoder.encode(credentials.password()),
				credentials.displayName(), now);
		// Following the link proved the address exactly as a confirmation link does, so
		// there is nothing to confirm and no second message to send. Without this the
		// account would be created straight into the state the gate refuses.
		user.markEmailVerified(now);
		return this.users.save(user);
	}

	/**
	 * The invitation behind a link, or the reason it is no longer one.
	 *
	 * <p>
	 * A link nobody recognises and one that has been spent give the same answer, because
	 * the advice is the same and neither is worth a code of its own. Expired and
	 * withdrawn are told apart: one can be sent again and the other was a decision, so a
	 * visitor given the wrong message would go and ask for exactly the wrong thing.
	 */
	private Invitation live(String rawToken) {
		Invitation invitation = this.invitations.findByTokenHash(LinkTokens.hash(rawToken))
			.orElseThrow(InvalidTokenException::new);
		if (invitation.getStatus() == InvitationStatus.REVOKED) {
			throw new InvitationRevokedException();
		}
		if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
			throw new InvalidTokenException();
		}
		if (invitation.hasExpired(Instant.now(this.clock))) {
			throw new InvitationExpiredException();
		}
		return invitation;
	}

	private Invitation outstanding(UUID invitationId, UUID tenantId) {
		return this.invitations.findPendingInTenant(invitationId, tenantId)
			.orElseThrow(InvitationNotFoundException::new);
	}

	/**
	 * An owner of the caller's organisation, for the two operations here that put a
	 * message in a stranger's inbox.
	 *
	 * <p>
	 * Who may administer an organisation is {@code MembershipService}'s rule, shared
	 * rather than restated: it reads the membership back instead of trusting the role
	 * pinned into a token twelve hours ago, and a second copy of that would be a second
	 * chance for one of them to drift.
	 *
	 * <p>
	 * The confirmation check on top is this package's own. Unreachable while the gate
	 * holds, since an unconfirmed account is refused a token — which is the reason to
	 * state it rather than trust the gate never to move. Somebody who has not shown they
	 * can read their own inbox may not write to anybody else's.
	 * @throws EmailNotVerifiedException if the caller's own address was never confirmed
	 */
	private Membership requireSendingOwner(UUID callerId, UUID tenantId) {
		Membership caller = this.memberships.requireOwner(callerId, tenantId);
		if (!caller.getUser().isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		return caller;
	}

	/**
	 * Sending cannot fail the caller: {@code EmailSender} logs delivery problems rather
	 * than throwing, and waits for the surrounding transaction to commit — so the row
	 * behind the link is durable before anybody can follow it.
	 */
	private void send(Invitation invitation, Membership inviter, String rawToken) {
		this.email.send(this.templates.invitation(invitation.getEmail(), inviter.getTenant().getName(),
				inviter.getUser().getDisplayName(), rawToken, VALID_FOR));
	}

}
