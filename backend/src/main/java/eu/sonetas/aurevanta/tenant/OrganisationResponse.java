package eu.sonetas.aurevanta.tenant;

import java.util.UUID;

/** An organisation as the API describes it, wherever one is named in a response. */
public record OrganisationResponse(UUID id, String name, String slug) {

	public static OrganisationResponse of(Tenant tenant) {
		return new OrganisationResponse(tenant.getId(), tenant.getName(), tenant.getSlug());
	}

}
