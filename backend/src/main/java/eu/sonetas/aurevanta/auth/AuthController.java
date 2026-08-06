package eu.sonetas.aurevanta.auth;

import eu.sonetas.aurevanta.security.AccessTokenService;
import eu.sonetas.aurevanta.security.AuthenticatedUser;
import eu.sonetas.aurevanta.user.User;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

	private final AccessTokenService accessTokenService;

	AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
			AccessTokenService accessTokenService) {
		this.registrationService = registrationService;
		this.authenticationService = authenticationService;
		this.accessTokenService = accessTokenService;
	}

	/**
	 * Creates an organisation with the caller as its owner, and signs them straight in.
	 */
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	AuthenticationResponse register(@Valid @RequestBody RegistrationRequest request) {
		User owner = this.registrationService.register(request);
		return AuthenticationResponse.of(owner, this.accessTokenService.issue(owner));
	}

	@PostMapping("/login")
	AuthenticationResponse login(@Valid @RequestBody LoginRequest request) {
		User user = this.authenticationService.authenticate(request);
		return AuthenticationResponse.of(user, this.accessTokenService.issue(user));
	}

	/** Lets a client holding a stored token restore its session on page load. */
	@GetMapping("/me")
	AccountResponse me(@AuthenticationPrincipal AuthenticatedUser caller) {
		return AccountResponse.of(this.authenticationService.requireAccount(caller.userId()));
	}

}
