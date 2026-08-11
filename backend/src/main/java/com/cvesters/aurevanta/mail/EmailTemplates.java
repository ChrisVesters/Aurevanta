package com.cvesters.aurevanta.mail;

import java.time.Duration;

import org.springframework.stereotype.Component;

/**
 * The wording of every message this application sends.
 *
 * <p>
 * Plain text assembled here rather than through a template engine: there are few
 * messages, they are short, and a body built by concatenation is one that can be read in
 * full at the point it is written. Introduce an engine when there is HTML to justify it,
 * not before.
 *
 * <p>
 * These are not translated. The recipient's language is not known at send time — mail
 * goes to addresses that may have no account yet — and guessing wrong is worse than
 * English.
 */
@Component
public class EmailTemplates {

	private final MailProperties properties;

	EmailTemplates(MailProperties properties) {
		this.properties = properties;
	}

	/**
	 * Asks someone to prove they can read the address they registered with.
	 * @param rawToken goes into the link, so it is the one thing here that must not be
	 * logged or stored
	 */
	public EmailMessage verifyEmail(String recipient, String displayName, String rawToken, Duration validFor) {
		// The token is base64url by construction, so every character is already safe in a
		// query string and encoding it would change nothing.
		String link = this.properties.link("/verify-email?token=" + rawToken);
		String body = """
				Hello %s,

				Confirm this address to finish setting up your Aurevanta account:

				%s

				The link works once and stops working after %s. If you did not create an
				account, you can ignore this message — nothing has been set up in your name
				that anyone can use.
				""".formatted(displayName, link, quantity(validFor.toDays(), "day"));
		return new EmailMessage(recipient, "Confirm your Aurevanta address", body);
	}

	/**
	 * Offers a way back into an account whose password is lost — and, under the
	 * verification gate, into one whose confirmation message never arrived.
	 * @param rawToken the strongest thing this application puts in an inbox: whoever
	 * holds it can take the account over, so it is never logged and never stored
	 */
	public EmailMessage resetPassword(String recipient, String displayName, String rawToken, Duration validFor) {
		String link = this.properties.link("/reset-password?token=" + rawToken);
		String body = """
				Hello %s,

				Choose a new password for your Aurevanta account:

				%s

				The link works once and stops working after %s. If you did not ask for a new
				password, you can ignore this message — nothing has changed, and the
				password you already have still works.
				""".formatted(displayName, link, quantity(validFor.toHours(), "hour"));
		return new EmailMessage(recipient, "Choose a new Aurevanta password", body);
	}

	/**
	 * Asks somebody to join an organisation.
	 *
	 * <p>
	 * The one message here that goes to a stranger: the recipient may never have heard of
	 * this application, so it names who is asking and what they are being asked to join
	 * before it names us. Without that it reads like the phishing it would otherwise be
	 * indistinguishable from.
	 * @param rawToken goes into the link, so it is the one thing here that must not be
	 * logged or stored
	 */
	public EmailMessage invitation(String recipient, String organisationName, String inviterName, String rawToken,
			Duration validFor) {
		String link = this.properties.link("/invite/" + rawToken);
		String body = """
				Hello,

				%s has invited you to join %s on Aurevanta, where teams plan work using
				estimates that admit what they do not know.

				%s

				The link works once and stops working after %s. If you were not expecting
				this, you can ignore this message — nothing has been set up in your name.
				""".formatted(inviterName, organisationName, link, quantity(validFor.toDays(), "day"));
		return new EmailMessage(recipient, inviterName + " invited you to " + organisationName + " on Aurevanta", body);
	}

	/**
	 * "3 days", "1 hour". A reset link lasts a single hour, so the singular is the common
	 * case rather than an edge one — and "1 hours" in a message asking somebody to trust
	 * a link is exactly the sort of thing that makes them not.
	 */
	private static String quantity(long count, String unit) {
		return (count == 1) ? count + " " + unit : count + " " + unit + "s";
	}

}
