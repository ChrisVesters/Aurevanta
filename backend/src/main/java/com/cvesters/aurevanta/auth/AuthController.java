package com.cvesters.aurevanta.auth;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.problem.InvalidCredentialsException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.SlugTakenException;
import com.cvesters.aurevanta.auth.registration.RegistrationRequest;
import com.cvesters.aurevanta.auth.registration.RegistrationService;
import com.cvesters.aurevanta.auth.signin.AuthenticationService;
import com.cvesters.aurevanta.auth.signin.IdentityResponse;
import com.cvesters.aurevanta.auth.signin.LoginRequest;
import com.cvesters.aurevanta.auth.signin.SignIn;
import com.cvesters.aurevanta.auth.signin.SignInResponse;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.ratelimit.SignInRateLimiter;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.security.AuthenticatedUser;
import com.cvesters.aurevanta.user.User;

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final RegistrationService registrationService;

	private final AuthenticationService authenticationService;

	private final MembershipService memberships;

	private final AccessTokenService accessTokenService;

	private final MailRateLimiter rateLimiter;

	private final SignInRateLimiter signInRateLimiter;

	AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
			MembershipService memberships, AccessTokenService accessTokenService, MailRateLimiter rateLimiter,
			SignInRateLimiter signInRateLimiter) {
		this.registrationService = registrationService;
		this.authenticationService = authenticationService;
		this.memberships = memberships;
		this.accessTokenService = accessTokenService;
		this.rateLimiter = rateLimiter;
		this.signInRateLimiter = signInRateLimiter;
	}

	/**
	 * Creates an organisation with the caller as its owner, and returns the account
	 * <em>without</em> a token.
	 *
	 * <p>
	 * Registering no longer signs anybody in. It cannot: an unconfirmed address is
	 * refused at sign-in, so handing out a session here would issue one to an account
	 * that is not yet allowed to have it — and would make the gate something a client
	 * could skip simply by registering.
	 *
	 * <p>
	 * Rate limited like the other two, though it looks less exposed than they do: an
	 * address can only be registered once, so nobody can be buried under confirmation
	 * mail this way. What it can do is send <em>one</em> unsolicited message each to as
	 * many addresses as somebody cares to type, which the per-source limit is what stops.
	 * An endpoint that emails an address the caller chose belongs behind this whether or
	 * not it can be made to email the same one twice.
	 *
	 * <p>
	 * A handle somebody else holds gives the recipient's claim back. Chosen handles made
	 * {@code slug_taken} a refusal this endpoint <em>invites</em> people to retry — the
	 * form fills in the free alternative it carries — and a retry loop in front of a
	 * three-per-quarter-hour budget locks somebody out of registering at all. It is
	 * decided without looking the address up and the caller has already been told why, so
	 * giving it back answers nothing that the refusal did not.
	 */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AccountResponse register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest incoming) {
		this.rateLimiter.claim(incoming.getRemoteAddr(), request.email());
		try {
			return AccountResponse.of(this.registrationService.register(request));
		}
		catch (SlugTakenException ex) {
			this.rateLimiter.refundRecipient(request.email());
			throw ex;
		}
	}

	/**
	 * Throttled on <em>failures</em>, which is the only thing here worth slowing down.
	 *
	 * <p>
	 * Without it the sole cost of guessing a password is how long bcrypt takes, which is
	 * a price per attempt and not a bound on how many attempts there are. Counting
	 * successes too would throttle somebody signing in from three devices, who is exactly
	 * the person this protects.
	 *
	 * <p>
	 * A wrong password on an unconfirmed account counts; {@code email_not_verified} does
	 * not. Reaching that answer means the password was right, so it is not a guess — and
	 * counting it would lock somebody out of the account they are trying to rescue.
	 */
	@PostMapping("/login")
	SignInResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest incoming) {
		String source = incoming.getRemoteAddr();
		this.signInRateLimiter.refuseIfExhausted(source, request.email());
		SignIn outcome;
		try {
			outcome = this.authenticationService.signIn(request);
		}
		catch (InvalidCredentialsException ex) {
			this.signInRateLimiter.recordFailure(source, request.email());
			throw ex;
		}
		this.signInRateLimiter.succeeded(request.email());
		return switch (outcome) {
			case SignIn.IntoOrganisation(Membership membership) -> SignInResponse
				.signedIn(AuthenticationResponse.of(membership, this.accessTokenService.issue(membership)));
			case SignIn.ChooseOrganisation(User user, List<Membership> held) ->
				SignInResponse.choosing(IdentityResponse.of(this.accessTokenService.issueIdentityToken(user), held));
		};
	}

	/** Lets a client holding a stored access token restore its session on page load. */
	@GetMapping("/me")
	AccountResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
		return AccountResponse.of(this.authenticationService.requireMembership(caller.userId(), caller.tenantId()));
	}

	/**
	 * Exchanges the caller's current token — identity or access — for one scoped to
	 * {@code tenantId}.
	 *
	 * <p>
	 * This is the one endpoint that takes an organisation from the request, and the only
	 * one where doing so is safe: the membership is looked up by the caller's own id
	 * together with the requested tenant, so an organisation they do not belong to
	 * produces no membership and no token. Widening it to look up by tenant alone would
	 * turn it into cross-tenant escalation.
	 * @throws NotAMemberException if the caller holds no membership in that organisation
	 */
	@PostMapping("/tenants/{tenantId}/token")
	AuthenticationResponse tokenForOrganisation(@AuthenticationPrincipal AuthenticatedUser caller,
			@PathVariable UUID tenantId) {
		Membership membership = this.memberships.select(caller.userId(), tenantId)
			.orElseThrow(NotAMemberException::new);
		return AuthenticationResponse.of(membership, this.accessTokenService.issue(membership));
	}

}
