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

}
