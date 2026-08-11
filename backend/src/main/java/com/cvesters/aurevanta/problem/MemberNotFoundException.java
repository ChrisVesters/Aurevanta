package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Nobody with that identifier belongs to the caller's organisation.
 *
 * <p>
 * One answer for "there is no such membership" and "it belongs to another organisation".
 * Telling those apart would let an owner discover which identifiers exist elsewhere,
 * which is precisely what the lookup by identifier <em>and</em> tenant exists to prevent.
 */
public class MemberNotFoundException extends ApiProblemException {

	public MemberNotFoundException() {
		super(HttpStatus.NOT_FOUND, "Member not found", "member_not_found",
				"Nobody with that identifier belongs to this organisation");
	}

}
