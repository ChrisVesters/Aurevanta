package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * One piece of work was said to need the same resource twice.
 *
 * <p>
 * Two rows for one pool would be two spellings of one number, and a scheduler adding them
 * up would be reading a data-entry mistake as a claim about a team. What somebody meant
 * is one row with the units they wanted.
 *
 * <p>
 * <strong>Decided from the request alone, before anything is looked up</strong> — the way
 * {@code self_dependency} is, and for the same reason: a body naming one pool twice is
 * wrong whatever else exists, and a caller who sent it learns nothing about which
 * resources are there by being told so. {@code uq_requirements_item_resource} answers the
 * same way for the two callers who pass that check in the same instant, so a race is
 * indistinguishable from the ordinary case.
 */
public class DuplicateRequirementException extends ApiProblemException {

	public DuplicateRequirementException() {
		super(HttpStatus.BAD_REQUEST, "Duplicate requirement", "duplicate_requirement",
				"A piece of work cannot need the same resource twice");
	}

}
