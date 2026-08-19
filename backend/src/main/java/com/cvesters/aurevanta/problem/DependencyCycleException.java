package com.cvesters.aurevanta.problem;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;

/**
 * The dependency asked for would make a plan wait for itself.
 *
 * <p>
 * The one refusal in this work that is a property of the whole graph rather than of any
 * row in it, which is why it is decided under a write lock on the plan — two edges can
 * each be acyclic against the graph as it was read and cyclic together.
 *
 * <p>
 * <strong>Carries the path that would have closed, in the order it would have
 * run.</strong> A refusal that says "this would create a cycle" and not <em>which</em>
 * cycle is a refusal somebody has to go and find by hand, across a plan of up to five
 * hundred items. The walk that decides the refusal already knows the route it took, so
 * naming it costs nothing — the same bargain {@link SlugTakenException} makes in offering
 * a free handle.
 *
 * <p>
 * Item identifiers rather than titles, because prose in a problem document is never shown
 * to anybody: the client already holds the plan it is drawing and looks each one up. The
 * list starts at the proposed predecessor and follows existing edges back to it, so the
 * last element leads to the first; the closing item is not repeated.
 */
public class DependencyCycleException extends ApiProblemException {

	private final List<UUID> path;

	public DependencyCycleException(List<UUID> path) {
		super(HttpStatus.CONFLICT, "Dependency cycle", "dependency_cycle",
				"That would make a piece of work wait for itself");
		this.path = List.copyOf(path);
	}

	public List<UUID> getPath() {
		return this.path;
	}

}
