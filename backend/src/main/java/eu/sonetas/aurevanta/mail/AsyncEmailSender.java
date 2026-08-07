package eu.sonetas.aurevanta.mail;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Hands a message to a background thread and returns, so nothing a user is waiting for is
 * held up by a mail server.
 *
 * <p>
 * <strong>Delivery failure is logged, never propagated.</strong> That is the whole point:
 * an unreachable SMTP server must not turn a successful registration into a failed one.
 * The cost is that a lost message is invisible to the caller and visible only in the log
 * — closing that needs delivery state in the database and a provider webhook, which
 * {@code docs/m1-plan.md} places after M1. Recipients appear in that log masked, because
 * a log of who uses this service is not something a delivery failure needs to create.
 */
class AsyncEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(AsyncEmailSender.class);

	private final EmailSender delegate;

	private final Executor executor;

	AsyncEmailSender(EmailSender delegate, Executor executor) {
		this.delegate = delegate;
		this.executor = executor;
	}

	/**
	 * Sends once the surrounding transaction commits, or straight away if there is none.
	 *
	 * <p>
	 * Waiting matters because the message usually describes something the same
	 * transaction has just written — a verification token, an invitation. Handing it to
	 * another thread immediately would race the commit, so a link could arrive before the
	 * row backing it exists; and if the transaction rolled back, a link would go out for
	 * a token that never will. Both leave a recipient holding something that does not
	 * work.
	 */
	@Override
	public void send(EmailMessage message) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					submit(message);
				}
			});
			return;
		}
		submit(message);
	}

	private void submit(EmailMessage message) {
		try {
			this.executor.execute(() -> deliver(message));
		}
		catch (RuntimeException ex) {
			// A full queue, or an executor shutting down. Being refused is still a lost
			// message, and it must not reach the caller either.
			logFailure(message, ex);
		}
	}

	private void deliver(EmailMessage message) {
		try {
			this.delegate.send(message);
		}
		catch (Exception ex) {
			logFailure(message, ex);
		}
	}

	private static void logFailure(EmailMessage message, Exception cause) {
		log.warn("Could not deliver '{}' to {}", message.subject(), masked(message.to()), cause);
	}

	/**
	 * Enough of the address to match a lost message against a support request, and not
	 * enough to be a list of who uses this service. Recipient addresses are personal
	 * data, and logs travel further than the database does — often to somewhere neither
	 * the retention policy nor the data-residency requirement reaches.
	 */
	private static String masked(String address) {
		int at = address.indexOf('@');
		return (at > 0) ? address.charAt(0) + "***" + address.substring(at) : "***";
	}

}
