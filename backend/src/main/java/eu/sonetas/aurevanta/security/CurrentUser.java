package eu.sonetas.aurevanta.security;

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

	public UUID requiredTenantId() {
		return require().tenantId();
	}

}
