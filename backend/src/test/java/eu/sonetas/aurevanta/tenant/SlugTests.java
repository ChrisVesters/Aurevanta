package eu.sonetas.aurevanta.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugTests {

	@Test
	void lowercasesAndHyphenatesWords() {
		assertThat(Slug.of("Acme Planning Co")).isEqualTo("acme-planning-co");
	}

	@Test
	void foldsAccentsToPlainLetters() {
		assertThat(Slug.of("Sonetas Europé")).isEqualTo("sonetas-europe");
	}

	@Test
	void collapsesRunsOfPunctuationIntoOneHyphen() {
		assertThat(Slug.of("Acme  --  Ltd.")).isEqualTo("acme-ltd");
	}

	@Test
	void trimsLeadingAndTrailingSeparators() {
		assertThat(Slug.of("  ...Acme...  ")).isEqualTo("acme");
	}

	@Test
	void isEmptyWhenTheNameHasNoLettersOrDigits() {
		assertThat(Slug.of("!!! ???")).isEmpty();
	}

	@Test
	void truncatesToTheColumnLengthWithoutLeavingATrailingHyphen() {
		String slug = Slug.of("a".repeat(Slug.MAX_LENGTH) + " overflow");

		assertThat(slug).hasSize(Slug.MAX_LENGTH).doesNotEndWith("-");
	}

}
