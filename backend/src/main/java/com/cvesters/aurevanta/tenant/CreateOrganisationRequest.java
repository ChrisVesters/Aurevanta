package com.cvesters.aurevanta.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The name of an organisation to start, and the handle it will answer to.
 *
 * @param slug required, because a refusal is only worth raising against something
 * somebody chose. The form proposes one from the name and accepting the proposal is a
 * choice; there is no path where the server picks and the caller is left holding the
 * consequence.
 */
public record CreateOrganisationRequest(@NotBlank @Size(max = 200) String name,

		@NotBlank @Pattern(regexp = Slug.PATTERN) @Size(min = Slug.MIN_LENGTH, max = Slug.MAX_LENGTH) String slug) {

	/**
	 * Stripped before validation, so that a name pasted with a trailing space is not
	 * stored with one. The handle is stripped for the same reason and no other: its
	 * pattern would refuse the space rather than ignore it, which is a confusing answer
	 * to a stray keystroke.
	 */
	public CreateOrganisationRequest {
		name = stripped(name);
		slug = stripped(slug);
	}

	private static String stripped(String value) {
		return (value != null) ? value.strip() : null;
	}

}
