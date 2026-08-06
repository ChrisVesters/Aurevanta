package eu.sonetas.aurevanta.security;

import java.util.UUID;

import eu.sonetas.aurevanta.user.UserRole;

/**
 * The caller behind the current request, as carried by their access token.
 *
 * <p>
 * {@link #tenantId()} is the isolation boundary: every query for tenant-owned data must
 * be constrained by it rather than trusting an identifier supplied in the request.
 */
public record AuthenticatedUser(UUID userId, UUID tenantId, String email, UserRole role) {
}
