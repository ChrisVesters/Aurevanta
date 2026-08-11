package com.cvesters.aurevanta.tenant;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a handle may be, and the alternative offered when one is taken.
 *
 * <p>
 * Deriving a handle from a display name is no longer here — it is a proposal a form makes
 * while somebody types, and it lives in the frontend with tests of its own. The pattern
 * below is the contract between the two, so it is the thing worth pinning down here.
 */
class SlugTests {

	private static final Pattern SHAPE = Pattern.compile(Slug.PATTERN);

	@ParameterizedTest
	@ValueSource(strings = { "acme", "acme-planning-co", "a1", "42", "acme-2" })
	void acceptsLowerCaseWordsSeparatedBySingleHyphens(String handle) {
		assertThat(SHAPE.matcher(handle).matches()).isTrue();
	}

	/**
	 * Everything here would either need escaping in a URL, or read as two handles that
	 * are the same handle.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "Acme", "acme co", "acme_co", "-acme", "acme-", "acme--co", "acme.co", "acmé", "" })
	void refusesAnythingElse(String handle) {
		assertThat(SHAPE.matcher(handle).matches()).isFalse();
	}

	@Test
	void countsOnFromAHandle() {
		assertThat(Slug.withSuffix("acme", 2)).isEqualTo("acme-2");
		assertThat(Slug.withSuffix("acme", 10)).isEqualTo("acme-10");
	}

	/** The suffix has to fit the column, so it is the handle that gives way. */
	@Test
	void makesRoomForTheSuffix() {
		String handle = Slug.withSuffix("a".repeat(Slug.MAX_LENGTH), 2);

		assertThat(handle).hasSize(Slug.MAX_LENGTH).endsWith("-2");
	}

	/**
	 * Somebody refused {@code acme-2} is plainly already counting, and a suggestion that
	 * appends to their count rather than continuing it reads as a mistake.
	 */
	@Test
	void countsOnFromWhereAHandleLeftOff() {
		assertThat(Slug.base("acme-2")).isEqualTo("acme");
		assertThat(Slug.base("acme-planning-co-17")).isEqualTo("acme-planning-co");
	}

	@Test
	void leavesAHandleThatIsNotCountingAlone() {
		assertThat(Slug.base("acme")).isEqualTo("acme");
		assertThat(Slug.base("acme-2-co")).isEqualTo("acme-2-co");
	}

	/** A handle that is nothing but a number has no base to count from but itself. */
	@Test
	void countsOnFromANumberedHandleWithNoWordInIt() {
		assertThat(Slug.base("42")).isEqualTo("42");
	}

}
