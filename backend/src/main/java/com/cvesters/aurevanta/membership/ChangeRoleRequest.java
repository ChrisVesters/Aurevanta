package com.cvesters.aurevanta.membership;

import com.cvesters.aurevanta.user.UserRole;

import jakarta.validation.constraints.NotNull;

/**
 * The standing to give somebody in the caller's organisation.
 *
 * <p>
 * The organisation is deliberately absent, as it is everywhere else: it comes from the
 * caller's own access token, which is the only source that cannot be pointed at somebody
 * else's.
 */
public record ChangeRoleRequest(@NotNull UserRole role) {
}
