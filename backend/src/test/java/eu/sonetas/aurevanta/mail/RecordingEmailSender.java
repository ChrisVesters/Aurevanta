package eu.sonetas.aurevanta.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps every message instead of sending it, so a test can assert what would have gone
 * out. Stands in for {@link EmailSender} everywhere except the one test that proves the
 * SMTP adapter itself.
 *
 * <p>
 * Synchronous on purpose: it replaces the whole port, wrapper included, so a test asserts
 * immediately after the call rather than waiting on a background thread.
 */
public class RecordingEmailSender implements EmailSender {

	private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

	@Override
	public void send(EmailMessage message) {
		this.sent.add(message);
	}

	public List<EmailMessage> sent() {
		return List.copyOf(this.sent);
	}

	/** The single message sent, refusing to guess if there was not exactly one. */
	public EmailMessage onlyMessage() {
		if (this.sent.size() != 1) {
			throw new AssertionError("Expected exactly one message, but " + this.sent.size() + " were sent");
		}
		return this.sent.getFirst();
	}

}
