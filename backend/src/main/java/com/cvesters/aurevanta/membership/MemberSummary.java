package com.cvesters.aurevanta.membership;

import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.user.UserRole;

/**
 * One person in an organisation, as their colleagues see them.
 *
 * <p>
 * The address is included because this is a list of people somebody already works with,
 * and a name alone cannot tell two of them apart. Nothing else about the account travels:
 * what a person holds in <em>other</em> organisations is not this organisation's
 * business, and when they were last here is not something a colleague needs to know.
 *
 * @param id names the membership, not the person — it is what a role change or a removal
 * addresses, and neither reaches across organisations
 */
public record MemberSummary(UUID id, UUID userId, String displayName, String email, UserRole role, Instant joinedAt) {

	static MemberSummary of(Membership membership) {
		return new MemberSummary(membership.getId(), membership.getUser().getId(),
				membership.getUser().getDisplayName(), membership.getUser().getEmail(), membership.getRole(),
				membership.getCreatedAt());
	}

}
