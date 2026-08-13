package com.cvesters.aurevanta.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What a plan is called and what is said about it, both sent together.
 *
 * <p>
 * The request carries the whole of what may be changed, as
 * {@code UpdateOrganisationRequest} does, because Jackson cannot tell an absent field
 * from a null one. For the name that settles it — a column that cannot be empty has no
 * reading of null that is right. For the description it is a genuine value: <strong>a
 * null clears it</strong>, which is the only way anybody could take back something they
 * wrote.
 */
public record UpdateProjectRequest(@NotBlank @Size(max = 200) String name,

		@Size(max = 2000) String description) {

	public UpdateProjectRequest {
		name = CreateProjectRequest.stripped(name);
		description = CreateProjectRequest.emptyToNull(CreateProjectRequest.stripped(description));
	}

}
