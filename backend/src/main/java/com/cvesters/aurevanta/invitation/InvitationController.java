package com.cvesters.aurevanta.invitation;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.auth.AuthenticationResponse;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * The first controller outside {@code auth}, which is the reason
 * {@code ApiExceptionHandler} no longer names a package: a {@code 429} raised here under
 * the old scoping would have lost its {@code code} and its {@code Retry-After} and
 * arrived as Boot's default error.
 *
 * <p>
 * Two of these endpoints need no credentials and the other four are for owners, which is
 * unusual enough to state: previewing and accepting are done by somebody who is being
 * invited <em>into</em> the organisation, so requiring a token scoped to it would be
 * requiring the thing they are asking for.
 */
@RestController
@RequestMapping("/api/invitations")
class InvitationController {

	private final InvitationService invitations;

	private final AccessTokenService accessTokens;

	private final MailRateLimiter rateLimiter;

	InvitationController(InvitationService invitations, AccessTokenService accessTokens, MailRateLimiter rateLimiter) {
		this.invitations = invitations;
		this.accessTokens = accessTokens;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * Invites an address into the organisation the caller's token is scoped to.
	 *
	 * <p>
	 * Rate limited like every other endpoint that emails an address somebody typed.
	 * Needing credentials is not what makes an endpoint safe here — the inbox on the
	 * receiving end belongs to a stranger either way, and it cannot tell an invitation
	 * from a confirmation link, which is why the two share one budget per recipient.
	 *
	 * <p>
	 * Claimed before anything is looked up, so it counts requests rather than messages: a
	 * limit spent only on invitations that turned out to be sendable would answer,
	 * through its own refusals, whether an address is already a member.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	InvitationResponse invite(@AuthenticationPrincipal AuthenticatedUser caller,
			@Valid @RequestBody CreateInvitationRequest request, HttpServletRequest incoming) {
		this.rateLimiter.claim(incoming.getRemoteAddr(), request.email());
		// Both identifiers come from the verified token. Taking either from the request
		// would make this a way to invite people into somebody else's organisation.
		return InvitationResponse
			.of(this.invitations.invite(caller.userId(), caller.tenantId(), request.email(), request.role()));
	}

	/** What is still outstanding, so an owner can chase, withdraw or resend it. */
	@GetMapping
	List<InvitationResponse> pending(@AuthenticationPrincipal AuthenticatedUser caller) {
		return this.invitations.pending(caller.userId(), caller.tenantId())
			.stream()
			.map(InvitationResponse::of)
			.toList();
	}

	/** Withdraws an invitation, freeing the address to be invited again. */
	@DeleteMapping("/{invitationId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revoke(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID invitationId) {
		this.invitations.revoke(caller.userId(), caller.tenantId(), invitationId);
	}

	/**
	 * Sends the invitation again, behind a new link.
	 *
	 * <p>
	 * The limit is claimed inside the service rather than here, because the recipient is
	 * named by the invitation and not by the request — see {@code InvitationService}.
	 */
	@PostMapping("/{invitationId}/resend")
	InvitationResponse resend(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID invitationId,
			HttpServletRequest incoming) {
		return InvitationResponse
			.of(this.invitations.resend(caller.userId(), caller.tenantId(), invitationId, incoming.getRemoteAddr()));
	}

	/**
	 * What an invitation offers, for whoever is holding the link.
	 *
	 * <p>
	 * Unauthenticated, and has to be: the person reading it may have no account at all,
	 * and deciding whether to make one is the whole reason they are shown this.
	 */
	@GetMapping("/{token}")
	InvitationPreview preview(@PathVariable String token) {
		return this.invitations.preview(token);
	}

	/**
	 * Accepts an invitation, and hands back a session for the organisation just joined.
	 *
	 * <p>
	 * The body is optional because only one of the two ways through needs it: somebody
	 * with no account yet sends a name and a password, and somebody who already has one
	 * signs in and sends nothing. A caller who is signed in and sends credentials anyway
	 * is answered by validation, which is the right answer — they are asking for an
	 * account they already have.
	 *
	 * <p>
	 * {@code caller} is null for a visitor with no token, which is a state this endpoint
	 * handles rather than refuses. Everything decided from it comes from a verified
	 * token.
	 */
	@PostMapping("/{token}/accept")
	AuthenticationResponse accept(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String token,
			@Valid @RequestBody(required = false) AcceptInvitationRequest credentials) {
		UUID callerId = (caller != null) ? caller.userId() : null;
		Membership joined = this.invitations.accept(token, callerId, credentials);
		return AuthenticationResponse.of(joined, this.accessTokens.issue(joined));
	}

}
