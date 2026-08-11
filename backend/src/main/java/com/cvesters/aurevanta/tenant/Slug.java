package com.cvesters.aurevanta.tenant;

/**
 * What an organisation's handle may be, and how to build the next one along.
 *
 * <p>
 * Deriving a handle from a display name used to live here and now lives in the frontend,
 * because it stopped being a rule and became a <em>suggestion</em>: the handle is a field
 * somebody fills in, and proposing a value while they type is what a form does. What
 * stays here is the part the server is answerable for — the shape a handle must have, and
 * the alternative offered when the one they chose is taken.
 *
 * <p>
 * {@link #PATTERN} is the contract between the two. A proposal the server would refuse is
 * a bug in the proposer, not a disagreement about the rule.
 */
public final class Slug {

	/**
	 * Lower-case letters and digits in groups separated by single hyphens: no leading,
	 * trailing or doubled hyphen, and nothing that would need escaping in a URL. Held as
	 * a string because {@code @Pattern} needs one.
	 */
	public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

	/** Two, so a handle is still something a person could say out loud. */
	public static final int MIN_LENGTH = 2;

	/** Matches {@code tenants.slug}. */
	public static final int MAX_LENGTH = 80;

	private Slug() {
	}

	/**
	 * The {@code n}th alternative to a handle, shortened if the suffix would not
	 * otherwise fit the column.
	 */
	public static String withSuffix(String base, int n) {
		String suffix = "-" + n;
		int room = MAX_LENGTH - suffix.length();
		return ((base.length() <= room) ? base : base.substring(0, room)) + suffix;
	}

	/**
	 * The handle a suggestion should count from.
	 *
	 * <p>
	 * Somebody refused {@code acme-2} is offered {@code acme-3} rather than
	 * {@code acme-2-2}: they are plainly already counting, and a suggestion that appends
	 * to their count instead of continuing it reads as a mistake.
	 */
	public static String base(String handle) {
		// Never empty for a handle of the shape PATTERN allows: it has to start with a
		// letter or a digit, and only a trailing count is taken off. "42" keeps itself.
		return handle.replaceAll("-\\d+$", "");
	}

}
