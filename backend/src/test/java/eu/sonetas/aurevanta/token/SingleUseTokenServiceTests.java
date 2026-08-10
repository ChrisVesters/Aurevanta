package eu.sonetas.aurevanta.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import eu.sonetas.aurevanta.TestcontainersConfiguration;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * These tokens let their holder take over an account without a password, so the two
 * properties worth proving are that one works exactly once and that a copy of the
 * database contains nothing anybody could replay.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SingleUseTokenServiceTests {

	private static final Duration AN_HOUR = Duration.ofHours(1);

	@Autowired
	private SingleUseTokenService tokenService;

	@Autowired
	private UserTokenRepository tokens;

	@Autowired
	private UserRepository users;

	private User ada;

	@BeforeEach
	void seedAnAccount() {
		this.tokens.deleteAll();
		this.users.deleteAll();
		this.ada = this.users
			.save(new User("ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", Instant.parse("2026-08-06T08:00:00Z")));
	}

	@Test
	void redeemsAnIssuedTokenForThePersonItBelongsTo() {
		SingleUseToken issued = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);

		assertThat(this.tokenService.consume(issued.value(), TokenPurpose.PASSWORD_RESET).map(User::getId))
			.contains(this.ada.getId());
	}

	@Test
	void reportsWhenTheTokenStopsWorking() {
		SingleUseToken issued = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);

		assertThat(issued.expiresAt()).isCloseTo(Instant.now().plus(AN_HOUR), within(10, ChronoUnit.SECONDS));
	}

	/**
	 * The point of the whole design: a stolen backup is a list of hashes, not a list of
	 * working password resets.
	 */
	@Test
	void writesDownOnlyAHashOfTheToken() throws Exception {
		SingleUseToken issued = this.tokenService.issue(this.ada, TokenPurpose.EMAIL_VERIFICATION, AN_HOUR);

		UserToken stored = this.tokens.findAll().getFirst();
		assertThat(stored.getTokenHash()).isEqualTo(sha256Hex(issued.value())).hasSize(64).isNotEqualTo(issued.value());
		// The hash is the only column that could hold it, so this is the whole search.
		assertThat(stored.getConsumedAt()).isNull();
	}

	@Test
	void issuesAUrlSafeTokenWithEnoughEntropyToBeUnguessable() {
		String first = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR).value();
		String second = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR).value();

		// 32 bytes, base64url, unpadded.
		assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void refusesATokenThatHasAlreadyBeenRedeemed() {
		SingleUseToken issued = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);
		this.tokenService.consume(issued.value(), TokenPurpose.PASSWORD_RESET);

		assertThat(this.tokenService.consume(issued.value(), TokenPurpose.PASSWORD_RESET)).isEmpty();
	}

	@Test
	void refusesAnExpiredToken() throws Exception {
		String raw = "a-token-that-has-timed-out";
		Instant past = Instant.now().minus(Duration.ofHours(2));
		this.tokens.save(new UserToken(this.ada, TokenPurpose.PASSWORD_RESET, sha256Hex(raw), past, past));

		assertThat(this.tokenService.consume(raw, TokenPurpose.PASSWORD_RESET)).isEmpty();
		// Refused, and left alone: nothing was spent.
		assertThat(this.tokens.findAll().getFirst().getConsumedAt()).isNull();
	}

	/**
	 * A confirmation link proves someone reads an inbox; a reset link lets them take the
	 * account over. Spending the weaker one as the stronger would erase that difference.
	 */
	@Test
	void refusesATokenIssuedForSomethingElse() {
		SingleUseToken issued = this.tokenService.issue(this.ada, TokenPurpose.EMAIL_VERIFICATION, AN_HOUR);

		assertThat(this.tokenService.consume(issued.value(), TokenPurpose.PASSWORD_RESET)).isEmpty();

		// Refused without being spent, so it still works for what it was issued for.
		assertThat(this.tokenService.consume(issued.value(), TokenPurpose.EMAIL_VERIFICATION).map(User::getId))
			.contains(this.ada.getId());
	}

	@Test
	void refusesATokenItNeverIssued() {
		assertThat(this.tokenService.consume("not-a-token-we-issued", TokenPurpose.PASSWORD_RESET)).isEmpty();
	}

	/**
	 * Someone who asked twice has two live links in their inbox. Acting on either one
	 * settles the matter, and a message read by anybody else afterwards must not still be
	 * a way in.
	 */
	@Test
	void spendsEverythingOutstandingForOnePurpose() {
		SingleUseToken first = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);
		SingleUseToken second = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);

		this.tokenService.revokeAll(this.ada, TokenPurpose.PASSWORD_RESET);

		assertThat(this.tokenService.consume(first.value(), TokenPurpose.PASSWORD_RESET)).isEmpty();
		assertThat(this.tokenService.consume(second.value(), TokenPurpose.PASSWORD_RESET)).isEmpty();
	}

	/**
	 * Purpose-by-purpose, or recovering a password would silently cancel a confirmation
	 * link the same person is still waiting to use.
	 */
	@Test
	void leavesTokensIssuedForSomethingElseAlone() {
		SingleUseToken confirmation = this.tokenService.issue(this.ada, TokenPurpose.EMAIL_VERIFICATION, AN_HOUR);

		this.tokenService.revokeAll(this.ada, TokenPurpose.PASSWORD_RESET);

		assertThat(this.tokenService.consume(confirmation.value(), TokenPurpose.EMAIL_VERIFICATION).map(User::getId))
			.contains(this.ada.getId());
	}

	/** One person's recovery must not cancel anybody else's. */
	@Test
	void leavesOtherPeoplesTokensAlone() {
		User grace = this.users
			.save(new User("grace@acme.test", "{bcrypt}$2a$10$hash", "Grace", Instant.parse("2026-08-06T08:00:00Z")));
		SingleUseToken hers = this.tokenService.issue(grace, TokenPurpose.PASSWORD_RESET, AN_HOUR);

		this.tokenService.revokeAll(this.ada, TokenPurpose.PASSWORD_RESET);

		assertThat(this.tokenService.consume(hers.value(), TokenPurpose.PASSWORD_RESET).map(User::getId))
			.contains(grace.getId());
	}

	/**
	 * A token is meaningless without the person it authenticates, so the foreign key
	 * cascades. Asserted because a missing {@code on delete cascade} would not show up
	 * until something deleted an account and tripped a constraint instead.
	 */
	@Test
	void discardsOutstandingTokensWhenTheAccountGoes() {
		this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);

		this.users.delete(this.ada);

		assertThat(this.tokens.findAll()).isEmpty();
	}

	/**
	 * SHA-256 is mandatory on every Java platform, so this cannot happen in production —
	 * but a hash that quietly became something else would make every token already issued
	 * unredeemable, so it fails loudly rather than being assumed away.
	 */
	@Test
	void refusesToCarryOnIfTheHashAlgorithmIsMissing() {
		assertThatThrownBy(() -> SingleUseTokenService.digest("SHA-000")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("SHA-000");
	}

	/**
	 * "Exactly once" has to hold when two clicks arrive together, not merely one after
	 * the other. Reading the row and then updating it would let both callers see an
	 * unspent token; a single conditional update cannot.
	 */
	@Test
	void redeemsOnlyOnceWhenSeveralAttemptsArriveTogether() throws Exception {
		SingleUseToken issued = this.tokenService.issue(this.ada, TokenPurpose.PASSWORD_RESET, AN_HOUR);
		int attempts = 4;
		CountDownLatch together = new CountDownLatch(1);

		List<Future<Optional<User>>> redemptions = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
			for (int attempt = 0; attempt < attempts; attempt++) {
				redemptions.add(pool.submit(() -> {
					together.await();
					return this.tokenService.consume(issued.value(), TokenPurpose.PASSWORD_RESET);
				}));
			}
			together.countDown();
		}

		long redeemed = 0;
		for (Future<Optional<User>> redemption : redemptions) {
			if (redemption.get().isPresent()) {
				redeemed++;
			}
		}
		assertThat(redeemed).isEqualTo(1);
	}

	private static String sha256Hex(String value) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

}
