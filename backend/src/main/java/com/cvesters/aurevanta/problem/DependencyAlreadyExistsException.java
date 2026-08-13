package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * That arrow has already been drawn.
 *
 * <p>
 * A second copy would say nothing the first does not, and two rows meaning one thing is
 * how a graph walk comes to count one path twice. {@code uq_dependencies_edge} is what
 * holds it; the pre-check in {@code DependencyService} is what makes the refusal
 * readable, and {@link ApiExceptionHandler} maps the index back to this same code for the
 * pair who get past that check in the same instant.
 *
 * <p>
 * Not a cycle, and deliberately not reported as one: the plan is already exactly as the
 * caller wants it, and telling somebody to rethink it would send them looking for a
 * problem that is not there.
 */
public class DependencyAlreadyExistsException extends ApiProblemException {

	public DependencyAlreadyExistsException() {
		super(HttpStatus.CONFLICT, "Dependency already exists", "dependency_already_exists",
				"That piece of work already has to finish before this one");
	}

}
