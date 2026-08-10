package com.cvesters.aurevanta.mail;

import com.cvesters.aurevanta.mail.AsyncEmailSender;
import com.cvesters.aurevanta.mail.EmailMessage;
import com.cvesters.aurevanta.mail.EmailSender;
import com.cvesters.aurevanta.mail.MailProperties;
import com.cvesters.aurevanta.mail.SmtpEmailSender;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one test that speaks real SMTP. Everything else stands the port down to
 * {@link RecordingEmailSender}, so this is the only place proving that what a caller
 * hands to {@link EmailSender} actually arrives as a message — recipient, subject, body
 * and the configured sender — rather than merely reaching a mock.
 */
class SmtpEmailSenderTests {

	private static final EmailMessage MESSAGE = new EmailMessage("ada@acme.test", "Confirm your address",
			"Follow this link to confirm: https://app.test/verify-email?token=abc");

	@RegisterExtension
	private static final GreenMailExtension SMTP = new GreenMailExtension(ServerSetupTest.SMTP);

	@Test
	void transmitsTheRecipientSubjectAndBody() throws Exception {
		sender(SMTP.getSmtp().getPort()).send(MESSAGE);

		assertThat(SMTP.waitForIncomingEmail(5000, 1)).isTrue();
		MimeMessage received = SMTP.getReceivedMessages()[0];
		assertThat(received.getAllRecipients()).extracting(Object::toString).containsExactly("ada@acme.test");
		assertThat(received.getSubject()).isEqualTo("Confirm your address");
		assertThat(GreenMailUtil.getBody(received)).contains("https://app.test/verify-email?token=abc");
	}

	/** The address is configuration, not something a caller passes in. */
	@Test
	void sendsFromTheConfiguredAddress() throws Exception {
		sender(SMTP.getSmtp().getPort()).send(MESSAGE);

		assertThat(SMTP.waitForIncomingEmail(5000, 1)).isTrue();
		assertThat(SMTP.getReceivedMessages()[0].getFrom()).extracting(Object::toString)
			.containsExactly("aurevanta@acme.test");
	}

	/**
	 * This adapter deliberately does not swallow failures — {@link AsyncEmailSender} is
	 * what decides they are survivable, and it can only do that if this one reports them.
	 */
	@Test
	void reportsAServerItCannotReach() {
		// Port 1 is reserved and nothing listens on it, so the connection is refused at
		// once rather than after a timeout.
		assertThatThrownBy(() -> sender(1).send(MESSAGE)).isInstanceOf(MailException.class);
	}

	private SmtpEmailSender sender(int port) {
		JavaMailSenderImpl transport = new JavaMailSenderImpl();
		transport.setHost("127.0.0.1");
		transport.setPort(port);
		transport.getJavaMailProperties().put("mail.smtp.connectiontimeout", "2000");
		return new SmtpEmailSender(transport, new MailProperties("aurevanta@acme.test", "https://app.test"));
	}

}
