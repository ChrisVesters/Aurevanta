package com.cvesters.aurevanta.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reads the caller out of the security context for code below the web layer.
 *
 * <p>
 * Services that touch tenant-owned data should take their tenant from
 * {@link #requiredTenantId()} rather than from a request parameter — that is what keeps
 * one tenant's data out of another's responses.
 */
@Component
public class CurrentUser {

	public Optional<AuthenticatedUser> find() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		return (authentication.getPrincipal() instanceof AuthenticatedUser user) ? Optional.of(user) : Optional.empty();
	}

	public AuthenticatedUser require() {
		return find().orElseThrow(() -> new AccessDeniedException("No authenticated user in the current context"));
	}

	/**
	 * The organisation the caller's token is pinned to.
	 * @throws AccessDeniedException if nobody is authenticated, or the caller holds an
	 * identity token and so has not chosen an organisation. Refusing is the point: a
	 * missing tenant must stop the query rather than quietly reach every row or none.
	 */
	public UUID requiredTenantId() {
		AuthenticatedUser user = require();
		if (!user.hasTenant()) {
			throw new AccessDeniedException("The current token is not scoped to an organisation");
		}
		return user.tenantId();
	}

}
