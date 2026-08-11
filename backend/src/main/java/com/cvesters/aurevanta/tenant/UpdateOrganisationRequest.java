package com.cvesters.aurevanta.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * What an owner may change about their organisation.
 *
 * <p>
 * Both are required rather than optional, as {@code PATCH /api/members/{id}} is: the
 * parts of a resource this API lets you change are the parts you send. It also keeps null
 * out of the question — Jackson cannot tell an absent field from one sent as null, so an
 * optional name would have to decide which of "leave it" and "clear it" a null meant, and
 * both readings are wrong for a column that cannot be empty.
 */
public record UpdateOrganisationRequest(@NotBlank @Size(max = 200) String name,

		@NotBlank @Pattern(regexp = Slug.PATTERN) @Size(min = Slug.MIN_LENGTH, max = Slug.MAX_LENGTH) String slug) {

	public UpdateOrganisationRequest {
		name = stripped(name);
		slug = stripped(slug);
	}

	private static String stripped(String value) {
		return (value != null) ? value.strip() : null;
	}

}
