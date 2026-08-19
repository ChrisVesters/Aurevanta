package com.cvesters.aurevanta.resource;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * A pool to declare.
 *
 * @param units how many of this thing there are. Required and positive, with no default:
 * a pool of nothing schedules nothing, and a server picking a number would be making a
 * claim about a team it has never met — which is the argument {@code capacity} has always
 * made and this is the thing that replaces it.
 * @param personId optional, because most pools are not a particular person. A pool of one
 * with nobody named is a perfectly good way to say "one designer, whoever that turns out
 * to be", and that is the case a screen must not force somebody past.
 */
public record CreateResourceRequest(@NotBlank @Size(max = 200) String name,

		@NotNull @Positive @Max(ResourceLimits.MOST_UNITS) Integer units,

		UUID personId) {

	/**
	 * Stripped before validation, so a name pasted with a trailing space is not stored
	 * with one.
	 */
	public CreateResourceRequest {
		name = (name != null) ? name.strip() : null;
	}

}
