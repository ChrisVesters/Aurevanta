package com.cvesters.aurevanta.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.cvesters.aurevanta.mail.AsyncEmailSender;
import com.cvesters.aurevanta.mail.EmailMessage;
import com.cvesters.aurevanta.mail.EmailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The wrapper that makes "registration does not fail if mail fails" true in practice.
 * Every path through it ends in the caller being unharmed, so what is asserted is both
 * that nothing escapes and that the loss was recorded — a failure swallowed in silence
 * would be worse than one that threw.
 */
class AsyncEmailSenderTests {

	private static final EmailMessage MESSAGE = new EmailMessage("ada@acme.test", "Confirm your address",
			"Follow the link.");

	/** Runs the task on the calling thread, so nothing here waits on a timer. */
	private static final Executor INLINE = Runnable::run;

	private final RecordingEmailSender delivered = new RecordingEmailSender();

	private ListAppender<ILoggingEvent> logged;

	private Logger logger;

	@BeforeEach
	void captureTheLog() {
		this.logged = new ListAppender<>();
		this.logged.start();
		this.logger = (Logger) LoggerFactory.getLogger(AsyncEmailSender.class);
		this.logger.addAppender(this.logged);
	}

	@AfterEach
	void releaseTheLog() {
		this.logger.detachAppender(this.logged);
	}

	@Test
	void passesTheMessageToTheTransport() {
		new AsyncEmailSender(this.delivered, INLINE).send(MESSAGE);

		assertThat(this.delivered.onlyMessage()).isEqualTo(MESSAGE);
		assertThat(warnings()).isEmpty();
	}

	/**
	 * The caller is a request thread; it must not be the one waiting on an SMTP server.
	 */
	@Test
	void handsTheWorkToTheExecutorRatherThanDoingItInline() {
		List<Runnable> deferred = new ArrayList<>();

		new AsyncEmailSender(this.delivered, deferred::add).send(MESSAGE);

		assertThat(this.delivered.sent()).isEmpty();
		deferred.forEach(Runnable::run);
		assertThat(this.delivered.sent()).containsExactly(MESSAGE);
	}

	@Test
	void reportsADeliveryFailureToTheLogAndNotToTheCaller() {
		EmailSender failing = (message) -> {
			throw new IllegalStateException("smtp is down");
		};

		assertThatCode(() -> new AsyncEmailSender(failing, INLINE).send(MESSAGE)).doesNotThrowAnyException();

		assertThat(warnings()).singleElement().satisfies((event) -> {
			assertThat(event.getFormattedMessage()).contains("Confirm your address");
			assertThat(event.getThrowableProxy().getMessage()).isEqualTo("smtp is down");
		});
	}

	/**
	 * Enough to match a lost message against a support request, not enough to make the
	 * log a list of who uses this service. Logs travel further than the database does.
	 */
	@Test
	void namesTheRecipientOnlyPartly() {
		EmailSender failing = (message) -> {
			throw new IllegalStateException("smtp is down");
		};

		new AsyncEmailSender(failing, INLINE).send(MESSAGE);

		assertThat(warnings()).singleElement().satisfies((event) -> {
			assertThat(event.getFormattedMessage()).contains("a***@acme.test").doesNotContain("ada@acme.test");
		});
	}

	@Test
	void namesNothingWhenTheRecipientHasNoLocalPart() {
		EmailSender failing = (message) -> {
			throw new IllegalStateException("smtp is down");
		};

		new AsyncEmailSender(failing, INLINE).send(new EmailMessage("@acme.test", "Subject", "Body"));

		assertThat(warnings()).singleElement()
			.satisfies((event) -> assertThat(event.getFormattedMessage()).contains("***").doesNotContain("acme.test"));
	}

	/**
	 * A bounded queue is what stops an unresponsive server growing the backlog without
	 * limit, so being refused is an expected outcome rather than a bug — and it is still
	 * a lost message, so it is logged like any other.
	 */
	@Test
	void reportsARefusedSubmissionToTheLogAndNotToTheCaller() {
		Executor full = (task) -> {
			throw new RejectedExecutionException("queue is full");
		};

		assertThatCode(() -> new AsyncEmailSender(this.delivered, full).send(MESSAGE)).doesNotThrowAnyException();

		assertThat(this.delivered.sent()).isEmpty();
		assertThat(warnings()).singleElement()
			.satisfies((event) -> assertThat(event.getThrowableProxy().getMessage()).isEqualTo("queue is full"));
	}

	private List<ILoggingEvent> warnings() {
		return this.logged.list.stream().filter((event) -> event.getLevel() == Level.WARN).toList();
	}

}
