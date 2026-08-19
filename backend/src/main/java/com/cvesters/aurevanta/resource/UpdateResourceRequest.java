package com.cvesters.aurevanta.resource;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * What a pool is to become.
 *
 * <p>
 * Every field is sent, including the ones that are not changing, for the reason
 * {@code UpdateProjectRequest} takes both of its: Jackson cannot tell an absent field
 * from a null one, so an optional name would have to decide which of "leave it" and
 * "clear it" a null meant — and only one of those readings is right for a column that
 * cannot be empty.
 *
 * @param personId a null clears the link rather than leaving it, which is the one field
 * here where that is a meaningful thing to ask for.
 */
public record UpdateResourceRequest(@NotBlank @Size(max = 200) String name,

		@NotNull @Positive @Max(ResourceLimits.MOST_UNITS) Integer units,

		UUID personId) {

	public UpdateResourceRequest {
		name = (name != null) ? name.strip() : null;
	}

}
