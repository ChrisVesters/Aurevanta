package com.cvesters.aurevanta.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A plan to start.
 *
 * @param description optional, because a project that has only just been named has
 * nothing said about it yet, and refusing it for that would be asking for prose before
 * there is anything to describe.
 */
public record CreateProjectRequest(@NotBlank @Size(max = 200) String name,

		@Size(max = 2000) String description) {

	/**
	 * Stripped before validation, so a name pasted with a trailing space is not stored
	 * with one — and so a description of nothing but spaces is stored as nothing rather
	 * than as whitespace something later has to know to ignore.
	 */
	public CreateProjectRequest {
		name = stripped(name);
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
