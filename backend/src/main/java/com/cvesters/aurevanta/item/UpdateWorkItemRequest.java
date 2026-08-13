package com.cvesters.aurevanta.item;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What an item is called and what is said about it, both sent together.
 *
 * <p>
 * As everywhere else in this API, the parts of a resource you may change are the parts
 * you send: Jackson cannot tell an absent field from a null one, and a title that cannot
 * be empty has no reading of null that is right. A null description is a value rather
 * than an omission, and clears it.
 */
public record UpdateWorkItemRequest(@NotBlank @Size(max = 200) String title,

		@Size(max = 2000) String description) {

	public UpdateWorkItemRequest {
		title = CreateWorkItemRequest.stripped(title);
		description = CreateWorkItemRequest.emptyToNull(CreateWorkItemRequest.stripped(description));
	}

}
