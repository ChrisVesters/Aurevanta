package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Somebody else already has the handle this caller asked for.
 *
 * <p>
 * The refusal M0 got wrong, aimed at the right thing. "That organisation name is taken"
 * left a person nothing to do, because it was their name; a handle is an address, and
 * picking another costs nothing. It is only ever raised against a handle somebody typed.
 *
 * <p>
 * Carries a free alternative so the refusal arrives with its own remedy, which is the
 * whole reason there is no availability endpoint to ask first. {@code suggested} is null
 * for the one caller that cannot offer one: a write that got past the check and met the
 * unique index instead, where the transaction is already lost and there is nothing left
 * to ask the database.
 */
public class SlugTakenException extends ApiProblemException {

	private final String suggested;

	public SlugTakenException(String suggested) {
		super(HttpStatus.CONFLICT, "Handle taken", "slug_taken", "That handle is already in use");
		this.suggested = suggested;
	}

	public String getSuggested() {
		return this.suggested;
	}

}
