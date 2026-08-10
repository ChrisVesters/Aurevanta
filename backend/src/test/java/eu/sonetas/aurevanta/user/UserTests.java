package eu.sonetas.aurevanta.user;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTests {

	@Test
	void holdsTheDetailsItWasCreatedWith() {
		Instant createdAt = Instant.parse("2026-08-06T08:00:00Z");

		User user = new User("ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", createdAt);

		assertThat(user.getEmail()).isEqualTo("ada@acme.test");
		assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hash");
		assertThat(user.getDisplayName()).isEqualTo("Ada");
		assertThat(user.getCreatedAt()).isEqualTo(createdAt);
		// Assigned by the persistence provider, so it is absent until the row is written.
		assertThat(user.getId()).isNull();
	}

	@Test
	void startsWithAnAddressNobodyHasProved() {
		assertThat(newUser().isEmailVerified()).isFalse();
	}

	@Test
	void remembersWhenTheAddressWasProved() {
		User user = newUser();

		user.markEmailVerified(Instant.parse("2026-08-07T09:00:00Z"));

		assertThat(user.isEmailVerified()).isTrue();
		assertThat(user.getEmailVerifiedAt()).isEqualTo(Instant.parse("2026-08-07T09:00:00Z"));
	}

	@Test
	void takesANewCredential() {
		User user = newUser();

		user.changePassword("{bcrypt}$2a$10$adifferenthash");

		assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$adifferenthash");
	}

	/**
	 * Recovering an account proves the address as surely as confirming it does, so a
	 * reset stamps an address nobody had proved yet. Someone whose confirmation message
	 * never arrived is otherwise locked out for good.
	 */
	@Test
	void aResetCanBeWhatProvesTheAddress() {
		User user = newUser();

		user.changePassword("{bcrypt}$2a$10$adifferenthash");
		user.markEmailVerified(Instant.parse("2026-08-07T09:00:00Z"));

		assertThat(user.isEmailVerified()).isTrue();
	}

	/**
	 * More than one route proves an address — a confirmation link, and from Step 6 a
	 * password reset — so arriving twice is expected and must not rewrite when it
	 * happened.
	 */
	@Test
	void keepsTheFirstTimeTheAddressWasProved() {
		User user = newUser();

		user.markEmailVerified(Instant.parse("2026-08-07T09:00:00Z"));
		user.markEmailVerified(Instant.parse("2026-08-09T17:00:00Z"));

		assertThat(user.getEmailVerifiedAt()).isEqualTo(Instant.parse("2026-08-07T09:00:00Z"));
	}

	private static User newUser() {
		return new User("ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", Instant.parse("2026-08-06T08:00:00Z"));
	}

}
