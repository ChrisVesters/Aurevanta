package com.cvesters.aurevanta.invitation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * The first controller outside {@code auth}, which is the reason
 * {@code ApiExceptionHandler} no longer names a package: a {@code 429} raised here under
 * the old scoping would have lost its {@code code} and its {@code Retry-After} and
 * arrived as Boot's default error.
 */
@RestController
@RequestMapping("/api/invitations")
class InvitationController {

	private final InvitationService invitations;

	private final MailRateLimiter rateLimiter;

	InvitationController(InvitationService invitations, MailRateLimiter rateLimiter) {
		this.invitations = invitations;
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

}
