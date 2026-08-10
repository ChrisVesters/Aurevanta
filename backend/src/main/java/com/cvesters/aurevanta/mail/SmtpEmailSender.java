package com.cvesters.aurevanta.mail;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Delivers over SMTP, and blocks until the server has accepted the message.
 *
 * <p>
 * SMTP rather than a provider's HTTP API because every transactional provider publishes
 * an SMTP endpoint too, which makes the choice of provider a matter of
 * {@code spring.mail.*} and nothing else. Reasons to swap this for an API client —
 * blocked outbound ports, per-message metadata, or volume — are recorded in
 * {@code docs/m1-plan.md}; when one arrives, the replacement implements
 * {@link EmailSender} and no caller changes.
 */
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender transport;

	private final MailProperties properties;

	SmtpEmailSender(JavaMailSender transport, MailProperties properties) {
		this.transport = transport;
		this.properties = properties;
	}

	/**
	 * @throws MailException if the server refuses the message or cannot be reached.
	 * Nothing catches this here on purpose: whether a failure is fatal is the caller's
	 * decision, and the wrapper around this one answers it for the whole application.
	 */
	@Override
	public void send(EmailMessage message) {
		SimpleMailMessage outbound = new SimpleMailMessage();
		outbound.setFrom(this.properties.from());
		outbound.setTo(message.to());
		outbound.setSubject(message.subject());
		outbound.setText(message.body());
		this.transport.send(outbound);
	}

}
