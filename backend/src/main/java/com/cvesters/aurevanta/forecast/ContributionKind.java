package com.cvesters.aurevanta.forecast;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What sort of thing a contribution is attributed to.
 *
 * <p>
 * A name on the wire rather than a shape the client infers from which fields are null,
 * for the reason every other refusal in this API carries a {@code code}: the browser
 * translates a name and never guesses from structure. The two that are not items are
 * named rather than numbered because they are read out loud — "the work nobody has
 * listed" is the answer somebody acts on.
 */
public enum ContributionKind {

	/** A piece of work the plan wrote down, named by its identifier. */
	ITEM("item"),

	/**
	 * Everything a run discovered that nobody had listed, added up.
	 *
	 * <p>
	 * Together rather than one by one: discovered work is different work in every run and
	 * there is nothing to rank. On a plan expected to grow it is frequently the largest
	 * source there is, which is `product-concept.md`'s whole point about scope
	 * uncertainty being the bigger of the two.
	 */
	DISCOVERED_WORK("discovered_work"),

	/**
	 * The one multiplier every item in a run shared.
	 *
	 * <p>
	 * The most useful row in the report when it is the largest, because it says that no
	 * estimate on the list is the problem.
	 */
	TEAM_FACTOR("team_factor");

	private final String code;

	ContributionKind(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return this.code;
	}

}
