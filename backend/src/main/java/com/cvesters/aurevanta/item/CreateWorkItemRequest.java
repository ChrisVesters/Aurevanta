package com.cvesters.aurevanta.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A piece of work to write down.
 *
 * <p>
 * A title and nothing else required. Entering a plan is mostly typing a list of things,
 * and a form that asked for prose about each one would be a form people stop filling in
 * halfway down their plan.
 */
public record CreateWorkItemRequest(@NotBlank @Size(max = 200) String title,

		@Size(max = 2000) String description) {

	public CreateWorkItemRequest {
		title = stripped(title);
		description = emptyToNull(stripped(description));
	}

	static String stripped(String value) {
		return (value != null) ? value.strip() : null;
	}

	/**
	 * A description somebody cleared and one they never wrote are the same absence, and
	 * storing them differently would give the column two spellings for it.
	 */
	static String emptyToNull(String value) {
		return ((value != null) && !value.isEmpty()) ? value : null;
	}

}
