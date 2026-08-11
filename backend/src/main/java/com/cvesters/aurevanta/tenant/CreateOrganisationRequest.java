package com.cvesters.aurevanta.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The name of an organisation to start. Bounded exactly as registration bounds it, since
 * the two produce the same row.
 */
public record CreateOrganisationRequest(@NotBlank @Size(max = 200) String name) {

	/**
	 * Stripped before validation, so that a name pasted with a trailing space is not
	 * stored with one — and so the handle is derived from the same text that was checked.
	 */
	public CreateOrganisationRequest {
		name = (name != null) ? name.strip() : null;
	}

}
