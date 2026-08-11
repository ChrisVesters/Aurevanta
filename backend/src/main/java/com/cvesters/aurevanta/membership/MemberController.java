package com.cvesters.aurevanta.membership;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * The people in one organisation.
 *
 * <p>
 * Distinct from {@code /api/memberships}, which is the same table read the other way
 * round: that answers "which organisations am I in", before one has been chosen, and this
 * answers "who else is in this one", once it has. The first is reachable with an identity
 * token and the second is not, which is why they are not one endpoint.
 */
@RestController
@RequestMapping("/api/members")
class MemberController {

	private final MembershipService memberships;

	MemberController(MembershipService memberships) {
		this.memberships = memberships;
	}

	/** Any member may see who their colleagues are. */
	@GetMapping
	List<MemberSummary> list(@AuthenticationPrincipal AuthenticatedUser caller) {
		return this.memberships.membersOf(caller.userId(), caller.tenantId()).stream().map(MemberSummary::of).toList();
	}

	/**
	 * Promotes or demotes somebody. Owners only, and never the last owner.
	 *
	 * <p>
	 * The membership is named in the path and the organisation is not: it comes from the
	 * caller's token, and the lookup pairs the two, so an identifier from another
	 * organisation selects nobody rather than somebody.
	 */
	@PatchMapping("/{membershipId}")
	MemberSummary changeRole(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID membershipId,
			@Valid @RequestBody ChangeRoleRequest request) {
		return MemberSummary
			.of(this.memberships.changeRole(caller.userId(), caller.tenantId(), membershipId, request.role()));
	}

	/** Takes somebody out of the organisation, leaving their account untouched. */
	@DeleteMapping("/{membershipId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID membershipId) {
		this.memberships.remove(caller.userId(), caller.tenantId(), membershipId);
	}

}
