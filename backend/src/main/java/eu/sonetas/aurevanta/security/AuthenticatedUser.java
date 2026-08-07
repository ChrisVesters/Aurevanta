package eu.sonetas.aurevanta.security;

import java.util.UUID;

import eu.sonetas.aurevanta.user.UserRole;

/**
 * The caller behind the current request, as carried by their token.
 *
 * <p>
 * {@link #tenantId()} is the isolation boundary: every query for tenant-owned data must
 * be constrained by it rather than trusting an identifier supplied in the request.
 *
 * @param tenantId the organisation this token acts for, or null when it is an identity
 * token — one that names a person before they have chosen an organisation
 * @param role the caller's standing in that organisation, null for the same reason
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, String email, UserRole role) {

	/** False for an identity token, which reaches nothing tenant-owned. */
	public boolean hasTenant() {
		return this.tenantId != null;
	}

}
