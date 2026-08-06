package eu.sonetas.aurevanta.auth;

import org.springframework.http.HttpStatus;

/**
 * Raised when a requested organisation name yields a handle another tenant already holds.
 * Names that differ only by punctuation or accents collide here, because both reduce to
 * the same handle.
 */
public class OrganisationNameUnavailableException extends AuthProblemException {

	public OrganisationNameUnavailableException() {
		super(HttpStatus.CONFLICT, "Organisation name unavailable", "organisation_name_unavailable",
				"That organisation name is already taken");
	}

}
