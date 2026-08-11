package com.cvesters.aurevanta.invitation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.mail.EmailSender;
import com.cvesters.aurevanta.mail.EmailTemplates;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.problem.AlreadyAMemberException;
import com.cvesters.aurevanta.problem.EmailNotVerifiedException;
import com.cvesters.aurevanta.problem.InvitationAlreadyPendingException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.NotAnOwnerException;
import com.cvesters.aurevanta.token.LinkTokens;
import com.cvesters.aurevanta.user.UserRole;

/** Puts an offer to join an organisation into somebody's inbox. */
@Service
public class InvitationService {

	/**
	 * Long enough that a message found after a holiday is still worth acting on, short
	 * enough that a link in an old mailbox is not a standing way into an organisation the
	 * recipient may by then have no business joining.
	 */
	static final Duration VALID_FOR = Duration.ofDays(7);

	private final InvitationRepository invitations;

	private final MembershipRepository memberships;

	private final EmailSender email;

	private final EmailTemplates templates;

	private final Clock clock;

	InvitationService(InvitationRepository invitations, MembershipRepository memberships, EmailSender email,
			EmailTemplates templates, Clock clock) {
		this.invitations = invitations;
		this.memberships = memberships;
		this.email = email;
		this.templates = templates;
		this.clock = clock;
	}

	/**
	 * Invites {@code email} to join {@code tenantId} as {@code role}, and sends the link.
	 *
	 * <p>
	 * The inviter's standing is read from the database rather than taken from the claims
	 * in their token. Those claims were true when the token was issued, and an access
	 * token lasts twelve hours: an owner demoted or removed in between would otherwise go
	 * on inviting people into an organisation they no longer administer, for the rest of
	 * the day.
	 * @param inviterId the caller, from their verified token and never from the request
	 * @param tenantId the organisation their token is scoped to, likewise
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws NotAnOwnerException if they belong to it but may not administer it
	 * @throws EmailNotVerifiedException if their own address has never been confirmed
	 * @throws AlreadyAMemberException if the address is already in this organisation
	 * @throws InvitationAlreadyPendingException if a live invitation is already out
	 */
	@Transactional
	public Invitation invite(UUID inviterId, UUID tenantId, String email, UserRole role) {
		Membership inviter = this.memberships.findForUserInTenant(inviterId, tenantId)
			.orElseThrow(NotAMemberException::new);
		if (inviter.getRole() != UserRole.OWNER) {
			throw new NotAnOwnerException();
		}
		// Under the gate this cannot happen — an unconfirmed account holds no token at
		// all. Stated anyway, because what it guards is somebody whose own address was
		// never proved writing to a stranger's inbox, and that should not rest on a rule
		// enforced somewhere else entirely.
		if (!inviter.getUser().isEmailVerified()) {
			throw new EmailNotVerifiedException();
		}
		if (this.memberships.existsInTenantByEmailIgnoringCase(tenantId, email)) {
			throw new AlreadyAMemberException();
		}

		Instant now = Instant.now(this.clock);
		Issued issued = issue(inviter, email, role, now);
		// Sending cannot fail the caller: EmailSender logs delivery problems rather than
		// throwing, and waits for this transaction to commit — so the row behind the link
		// is durable before anybody can follow it.
		this.email.send(this.templates.invitation(email, inviter.getTenant().getName(),
				inviter.getUser().getDisplayName(), issued.rawToken(), VALID_FOR));
		return issued.invitation();
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
	private Issued issue(Membership inviter, String email, UserRole role, Instant now) {
		String rawToken = LinkTokens.generate();
		String tokenHash = LinkTokens.hash(rawToken);
		Instant expiresAt = now.plus(VALID_FOR);
		Invitation outstanding = this.invitations.findPending(inviter.getTenant().getId(), email).orElse(null);
		if (outstanding == null) {
			return new Issued(this.invitations
				.save(new Invitation(inviter.getTenant(), email, role, inviter.getUser(), tokenHash, expiresAt, now)),
					rawToken);
		}
		if (!outstanding.hasExpired(now)) {
			throw new InvitationAlreadyPendingException();
		}
		outstanding.renew(role, inviter.getUser(), tokenHash, expiresAt, now);
		return new Issued(this.invitations.save(outstanding), rawToken);
	}

	/** The raw token exists only between being generated and going into the message. */
	private record Issued(Invitation invitation, String rawToken) {
	}

}
