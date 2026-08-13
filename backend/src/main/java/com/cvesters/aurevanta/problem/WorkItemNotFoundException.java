package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * No work item with that identifier belongs to the caller's organisation.
 *
 * <p>
 * One answer for "there is no such item" and "it belongs to another organisation", as
 * {@link ProjectNotFoundException} and {@link MemberNotFoundException} are. Telling them
 * apart would turn the endpoint into a way of discovering which identifiers exist
 * elsewhere.
 */
public class WorkItemNotFoundException extends ApiProblemException {

	public WorkItemNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Work item not found", "work_item_not_found",
				"No work item with that identifier belongs to this organisation");
	}

}
