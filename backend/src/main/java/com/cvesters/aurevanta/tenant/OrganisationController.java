package com.cvesters.aurevanta.tenant;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.auth.AuthenticationResponse;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * Starting an organisation from an account that already exists.
 *
 * <p>
 * Reachable with an <em>identity</em> token as well as an access one, which is the whole
 * point: the person who needs this most is somebody who belongs to nothing and therefore
 * has no access token to offer. Losing your last membership became possible the moment an
 * owner could remove people, and without this the only way back would be waiting for
 * somebody else to invite you.
 */
@RestController
@RequestMapping("/api/organisations")
class OrganisationController {

	private final OrganisationService organisations;

	private final AccessTokenService accessTokens;

	OrganisationController(OrganisationService organisations, AccessTokenService accessTokens) {
		this.organisations = organisations;
		this.accessTokens = accessTokens;
	}

	/**
	 * Answers with a session for the new organisation, so the caller is working in it
	 * immediately rather than having to exchange a token for the thing they just made.
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AuthenticationResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
			@Valid @RequestBody CreateOrganisationRequest request) {
		Membership owner = this.organisations.create(caller.userId(), request.name(), request.slug());
		return AuthenticationResponse.of(owner, this.accessTokens.issue(owner));
	}

}
