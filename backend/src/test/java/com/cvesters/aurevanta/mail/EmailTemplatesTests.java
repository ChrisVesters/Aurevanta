package com.cvesters.aurevanta.mail;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.mail.EmailMessage;
import com.cvesters.aurevanta.mail.EmailTemplates;
import com.cvesters.aurevanta.mail.MailProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the recipient actually reads. Asserted on the parts that have to be right for the
 * message to do its job — the link, and how long it lasts — rather than on the prose
 * around them, which is free to be rewritten without breaking a test.
 */
class EmailTemplatesTests {

	private final EmailTemplates templates = new EmailTemplates(new MailProperties(null, "https://app.acme.test"));

	@Test
	void buildsAConfirmationLinkAgainstTheConfiguredOrigin() {
		EmailMessage message = this.templates.verifyEmail("ada@acme.test", "Ada", "a-raw-token", Duration.ofDays(3));

		assertThat(message.to()).isEqualTo("ada@acme.test");
		assertThat(message.subject()).isEqualTo("Confirm your Aurevanta address");
		assertThat(message.body()).contains("Hello Ada,")
			.contains("https://app.acme.test/verify-email?token=a-raw-token")
			.contains("stops working after 3 days");
	}

	@Test
	void buildsAResetLinkAgainstTheConfiguredOrigin() {
		EmailMessage message = this.templates.resetPassword("ada@acme.test", "Ada", "a-raw-token", Duration.ofHours(1));

		assertThat(message.to()).isEqualTo("ada@acme.test");
		assertThat(message.subject()).isEqualTo("Choose a new Aurevanta password");
		assertThat(message.body()).contains("Hello Ada,")
			.contains("https://app.acme.test/reset-password?token=a-raw-token")
			.contains("stops working after 1 hour");
	}

	/**
	 * The one message that goes to somebody who may never have heard of this application,
	 * so who is asking and what they are being asked to join have to survive any rewrite
	 * of the prose around them. Without both, it is indistinguishable from phishing.
	 */
	@Test
	void namesTheInviterAndTheOrganisationInAnInvitation() {
		EmailMessage message = this.templates.invitation("dave@elsewhere.test", "Acme Planning Co", "Ada",
				"a-raw-token", Duration.ofDays(7));

		assertThat(message.to()).isEqualTo("dave@elsewhere.test");
		assertThat(message.subject()).contains("Ada").contains("Acme Planning Co");
		assertThat(message.body()).contains("Ada")
			.contains("Acme Planning Co")
			.contains("https://app.acme.test/invite/a-raw-token")
			.contains("stops working after 7 days");
	}

	/**
	 * A reset link lasts a single hour, so the singular is the ordinary case and the
	 * plural the exception — the opposite way round from most pluralisation, and the
	 * reason it is worth stating rather than assuming.
	 */
	@Test
	void countsInWordsThatReadCorrectlyEitherWay() {
		assertThat(this.templates.resetPassword("ada@acme.test", "Ada", "t", Duration.ofHours(2)).body())
			.contains("after 2 hours");
		assertThat(this.templates.verifyEmail("ada@acme.test", "Ada", "t", Duration.ofDays(1)).body())
			.contains("after 1 day");
	}

	/**
	 * Every character a token can hold is already safe in a query string, so the link is
	 * assembled rather than encoded. If token generation ever stops being base64url this
	 * is where it breaks.
	 */
	@Test
	void putsTheTokenInTheLinkUntouched() {
		String token = "abcXYZ012_-";

		assertThat(this.templates.resetPassword("ada@acme.test", "Ada", token, Duration.ofHours(1)).body())
			.contains("?token=" + token);
	}

}
