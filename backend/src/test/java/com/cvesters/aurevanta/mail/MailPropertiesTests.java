package com.cvesters.aurevanta.mail;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.mail.MailProperties;

import static org.assertj.core.api.Assertions.assertThat;

class MailPropertiesTests {

	@Test
	void fallsBackToADevelopmentSenderAndOrigin() {
		MailProperties properties = new MailProperties(null, null);

		assertThat(properties.from()).isEqualTo("no-reply@localhost");
		assertThat(properties.baseUrl()).isEqualTo("http://localhost:5173");
	}

	@Test
	void treatsBlankConfigurationAsAbsent() {
		MailProperties properties = new MailProperties("  ", "  ");

		assertThat(properties.from()).isEqualTo("no-reply@localhost");
		assertThat(properties.baseUrl()).isEqualTo("http://localhost:5173");
	}

	@Test
	void keepsWhatIsConfigured() {
		MailProperties properties = new MailProperties("hello@acme.test", "https://app.acme.test");

		assertThat(properties.from()).isEqualTo("hello@acme.test");
		assertThat(properties.baseUrl()).isEqualTo("https://app.acme.test");
	}

	/**
	 * Both spellings of the origin, and both of the path, are natural to write in
	 * configuration — and a doubled slash in an emailed link looks broken to a recipient
	 * even where it still resolves.
	 */
	@Test
	void buildsTheSameLinkHoweverTheOriginAndPathAreSpelled() {
		String expected = "https://app.acme.test/verify-email?token=abc";

		assertThat(new MailProperties(null, "https://app.acme.test").link("/verify-email?token=abc"))
			.isEqualTo(expected);
		assertThat(new MailProperties(null, "https://app.acme.test/").link("/verify-email?token=abc"))
			.isEqualTo(expected);
		assertThat(new MailProperties(null, "https://app.acme.test").link("verify-email?token=abc"))
			.isEqualTo(expected);
		assertThat(new MailProperties(null, "https://app.acme.test/").link("verify-email?token=abc"))
			.isEqualTo(expected);
	}

}
