package com.cvesters.aurevanta.membership;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/memberships")
class MembershipController {

	private final MembershipService memberships;

	MembershipController(MembershipService memberships) {
		this.memberships = memberships;
	}

	/**
	 * The organisations the caller belongs to. Reachable with an identity token as well
	 * as an access token, because choosing an organisation is precisely what an identity
	 * token exists for; the list is scoped to the caller's own id either way.
	 */
	@GetMapping
	List<MembershipSummary> list(@AuthenticationPrincipal AuthenticatedUser caller) {
		return this.memberships.forUser(caller.userId()).stream().map(MembershipSummary::of).toList();
	}

}
