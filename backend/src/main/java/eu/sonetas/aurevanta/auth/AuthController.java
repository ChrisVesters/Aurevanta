package eu.sonetas.aurevanta.auth;

import java.util.List;
import java.util.UUID;

import eu.sonetas.aurevanta.auth.problem.NotAMemberException;
import eu.sonetas.aurevanta.auth.registration.RegistrationRequest;
import eu.sonetas.aurevanta.auth.registration.RegistrationService;
import eu.sonetas.aurevanta.auth.signin.AuthenticationService;
import eu.sonetas.aurevanta.auth.signin.IdentityResponse;
import eu.sonetas.aurevanta.auth.signin.LoginRequest;
import eu.sonetas.aurevanta.auth.signin.SignIn;
import eu.sonetas.aurevanta.auth.signin.SignInResponse;
import eu.sonetas.aurevanta.membership.Membership;
import eu.sonetas.aurevanta.membership.MembershipService;
import eu.sonetas.aurevanta.security.AccessTokenService;
import eu.sonetas.aurevanta.security.AuthenticatedUser;
import eu.sonetas.aurevanta.user.User;
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

@RestController
@RequestMapping("/api/auth")
class AuthController {

	private final RegistrationService registrationService;

	private final AuthenticationService authenticationService;

	private final MembershipService memberships;

	private final AccessTokenService accessTokenService;

	AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
			MembershipService memberships, AccessTokenService accessTokenService) {
		this.registrationService = registrationService;
		this.authenticationService = authenticationService;
		this.memberships = memberships;
		this.accessTokenService = accessTokenService;
	}

	/**
	 * Creates an organisation with the caller as its owner, and signs them straight in.
	 * One membership exists by construction, so there is nothing to choose between.
	 */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthenticationResponse register(@Valid @RequestBody RegistrationRequest request) {
		Membership owner = this.registrationService.register(request);
		return AuthenticationResponse.of(owner, this.accessTokenService.issue(owner));
	}

	@PostMapping("/login")
	SignInResponse login(@Valid @RequestBody LoginRequest request) {
		return switch (this.authenticationService.signIn(request)) {
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
