package eu.sonetas.aurevanta.tenant;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantTests {

	@Test
	void holdsTheDetailsItWasCreatedWith() {
		Instant createdAt = Instant.parse("2026-08-06T08:00:00Z");

		Tenant tenant = new Tenant("Acme Planning Co", "acme-planning-co", createdAt);

		assertThat(tenant.getName()).isEqualTo("Acme Planning Co");
		assertThat(tenant.getSlug()).isEqualTo("acme-planning-co");
		assertThat(tenant.getCreatedAt()).isEqualTo(createdAt);
		// Assigned by the persistence provider, so it is absent until the row is written.
		assertThat(tenant.getId()).isNull();
	}

}
