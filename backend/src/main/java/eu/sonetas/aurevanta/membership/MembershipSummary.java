package eu.sonetas.aurevanta.membership;

import java.time.Instant;
import java.util.UUID;

import eu.sonetas.aurevanta.tenant.OrganisationResponse;
import eu.sonetas.aurevanta.user.UserRole;

/**
 * One of the caller's organisations, and their role in it.
 *
 * <p>
 * Deliberately says nothing about the organisation beyond its name and handle: this is
 * the list shown before a tenant has been chosen, so it must not disclose anything a
 * tenant-scoped token would be needed for.
 *
 * @param lastAccessedAt when the caller last chose this organisation, or null if never
 */
public record MembershipSummary(UUID id, UserRole role, OrganisationResponse organisation, Instant lastAccessedAt) {

	public static MembershipSummary of(Membership membership) {
		return new MembershipSummary(membership.getId(), membership.getRole(),
				OrganisationResponse.of(membership.getTenant()), membership.getLastAccessedAt());
	}

}
