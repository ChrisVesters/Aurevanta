package eu.sonetas.aurevanta.membership;

import java.time.Instant;

import eu.sonetas.aurevanta.tenant.Tenant;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MembershipTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-06T08:00:00Z");

	@Test
	void holdsTheDetailsItWasCreatedWith() {
		Tenant tenant = new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT);
		User user = new User("ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", CREATED_AT);

		Membership membership = new Membership(user, tenant, UserRole.OWNER, CREATED_AT);

		assertThat(membership.getUser()).isSameAs(user);
		assertThat(membership.getTenant()).isSameAs(tenant);
		assertThat(membership.getRole()).isEqualTo(UserRole.OWNER);
		assertThat(membership.getCreatedAt()).isEqualTo(CREATED_AT);
		// Assigned by the persistence provider, so it is absent until the row is written.
		assertThat(membership.getId()).isNull();
	}

	@Test
	void hasNotBeenAccessedUntilItIsChosen() {
		assertThat(membership().getLastAccessedAt()).isNull();
	}

	@Test
	void remembersTheMostRecentTimeItWasChosen() {
		Membership membership = membership();

		membership.recordAccess(Instant.parse("2026-08-07T09:00:00Z"));
		membership.recordAccess(Instant.parse("2026-08-07T17:30:00Z"));

		assertThat(membership.getLastAccessedAt()).isEqualTo(Instant.parse("2026-08-07T17:30:00Z"));
	}

	private Membership membership() {
		return new Membership(new User("ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", CREATED_AT),
				new Tenant("Acme", "acme", CREATED_AT), UserRole.MEMBER, CREATED_AT);
	}

}
