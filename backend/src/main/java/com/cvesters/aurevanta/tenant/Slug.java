package com.cvesters.aurevanta.tenant;

import java.text.Normalizer;
import java.util.Locale;

/** Derives the URL-safe handle for a tenant from its display name. */
public final class Slug {

	public static final int MAX_LENGTH = 80;

	private Slug() {
	}

	/**
	 * Folds accents, lowercases, and reduces anything that is not a letter or digit to a
	 * single hyphen.
	 * @return the slug, or an empty string if the name held no usable characters
	 */
	public static String of(String name) {
		String folded = Normalizer.normalize(name, Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "")
			.toLowerCase(Locale.ROOT);
		String hyphenated = folded.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
		if (hyphenated.length() <= MAX_LENGTH) {
			return hyphenated;
		}
		return hyphenated.substring(0, MAX_LENGTH).replaceAll("-+$", "");
	}

}
