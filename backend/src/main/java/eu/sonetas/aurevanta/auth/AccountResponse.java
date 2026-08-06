package eu.sonetas.aurevanta.auth;

import java.util.UUID;

import eu.sonetas.aurevanta.tenant.Tenant;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRole;

/** The signed-in user and the organisation they act within. */
public record AccountResponse(UUID userId, String email, String displayName, UserRole role,
		OrganisationResponse organisation) {

	static AccountResponse of(User user) {
		Tenant tenant = user.getTenant();
		return new AccountResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
				new OrganisationResponse(tenant.getId(), tenant.getName(), tenant.getSlug()));
	}

	public record OrganisationResponse(UUID id, String name, String slug) {
	}

}
