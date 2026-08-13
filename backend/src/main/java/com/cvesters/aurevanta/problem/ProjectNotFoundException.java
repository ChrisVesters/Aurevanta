package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * No project with that identifier belongs to the caller's organisation.
 *
 * <p>
 * One answer for "there is no such project" and "it belongs to another organisation", as
 * {@link MemberNotFoundException} is. Telling them apart would make the endpoint a way to
 * discover which identifiers exist elsewhere, which is exactly what looking a project up
 * by identifier <em>and</em> tenant exists to prevent.
 */
public class ProjectNotFoundException extends ApiProblemException {

	public ProjectNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Project not found", "project_not_found",
				"No project with that identifier belongs to this organisation");
	}

}
