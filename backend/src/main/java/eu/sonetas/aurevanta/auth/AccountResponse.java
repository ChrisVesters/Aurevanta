package eu.sonetas.aurevanta.auth;

import java.util.UUID;

import eu.sonetas.aurevanta.membership.Membership;
import eu.sonetas.aurevanta.tenant.OrganisationResponse;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRole;

/**
 * The signed-in user and the organisation they are acting within — that is, one
 * membership. The role is the one held <em>here</em>; the same person may hold another
 * elsewhere.
 */
public record AccountResponse(UUID userId, String email, String displayName, UserRole role,
		OrganisationResponse organisation) {

	static AccountResponse of(Membership membership) {
		User user = membership.getUser();
		return new AccountResponse(user.getId(), user.getEmail(), user.getDisplayName(), membership.getRole(),
				OrganisationResponse.of(membership.getTenant()));
	}

}
