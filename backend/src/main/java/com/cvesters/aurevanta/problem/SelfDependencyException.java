package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * A piece of work was made to wait for itself.
 *
 * <p>
 * The shortest cycle there is, and the only one that can be refused without reading a
 * single row — so it is answered first, before the items are looked up, on the same
 * grounds as an estimate whose ends are the wrong way round: it is a fact about the
 * request alone, and a caller who sent nonsense learns nothing about which items exist by
 * being told so.
 *
 * <p>
 * Its own code rather than {@link DependencyCycleException} because the remedy is
 * different. A cycle is a plan somebody has to go and rethink; this is two boxes with the
 * same thing in them.
 */
public class SelfDependencyException extends ApiProblemException {

	public SelfDependencyException() {
		super(HttpStatus.BAD_REQUEST, "Self dependency", "self_dependency", "A piece of work cannot depend on itself");
	}

}
