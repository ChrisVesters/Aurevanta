package eu.sonetas.aurevanta.mail;

import java.util.concurrent.Executor;

import eu.sonetas.aurevanta.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mail almost always describes something the same transaction has just written — a
 * verification token, an invitation. Sending it before that transaction commits is a race
 * the recipient loses: they follow a link to a row that does not exist yet, or to one
 * that never will because the transaction rolled back.
 *
 * <p>
 * Driven through a real transaction manager rather than a stubbed one, because what is
 * being proved is the interaction with Spring's synchronisation, not our own branch.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AsyncEmailSenderTransactionTests {

	private static final EmailMessage MESSAGE = new EmailMessage("ada@acme.test", "Confirm your address",
			"Follow the link.");

	/** Runs the task on the calling thread, so nothing here waits on a timer. */
	private static final Executor INLINE = Runnable::run;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final RecordingEmailSender delivered = new RecordingEmailSender();

	@Test
	void holdsTheMessageBackUntilTheTransactionCommits() {
		AsyncEmailSender sender = new AsyncEmailSender(this.delivered, INLINE);

		transactions().executeWithoutResult((status) -> {
			sender.send(MESSAGE);
			// Still inside the transaction: the row this message talks about is not
			// visible to anyone else yet, so neither should the message be.
			assertThat(this.delivered.sent()).isEmpty();
		});

		assertThat(this.delivered.onlyMessage()).isEqualTo(MESSAGE);
	}

	@Test
	void sendsNothingWhenTheTransactionRollsBack() {
		AsyncEmailSender sender = new AsyncEmailSender(this.delivered, INLINE);

		transactions().executeWithoutResult((status) -> {
			sender.send(MESSAGE);
			status.setRollbackOnly();
		});

		assertThat(this.delivered.sent()).isEmpty();
	}

	/** Outside a transaction there is nothing to wait for, so it goes at once. */
	@Test
	void sendsImmediatelyWhenThereIsNoTransaction() {
		new AsyncEmailSender(this.delivered, INLINE).send(MESSAGE);

		assertThat(this.delivered.onlyMessage()).isEqualTo(MESSAGE);
	}

	private TransactionTemplate transactions() {
		return new TransactionTemplate(this.transactionManager);
	}

}
