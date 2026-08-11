package com.cvesters.aurevanta.auth.registration;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.auth.registration.RegistrationRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalising here rather than in the service is what makes it happen <em>before</em>
 * validation, which is the whole point: {@code @Email} rejects a padded address, so
 * stripping afterwards would be too late to help anyone who pasted one.
 */
class RegistrationRequestTests {

	@Test
	void stripsTheTextItWillStore() {
		RegistrationRequest request = new RegistrationRequest("  Acme  ", "  acme  ", "  Ada  ", "  ada@acme.test  ",
				"secret");

		assertThat(request.organisationName()).isEqualTo("Acme");
		assertThat(request.displayName()).isEqualTo("Ada");
		assertThat(request.email()).isEqualTo("ada@acme.test");
	}

	/**
	 * Spaces are legitimate characters in a passphrase. Stripping one would store a
	 * different credential than was chosen, and then refuse the one that gets typed.
	 */
	@Test
	void leavesThePasswordExactlyAsItWasTyped() {
		assertThat(new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", "  pass phrase  ").password())
			.isEqualTo("  pass phrase  ");
	}

	/** A missing field has to survive as far as validation, which is what reports it. */
	@Test
	void toleratesMissingValues() {
		RegistrationRequest request = new RegistrationRequest(null, null, null, null, null);

		assertThat(request.organisationName()).isNull();
		assertThat(request.displayName()).isNull();
		assertThat(request.email()).isNull();
		assertThat(request.password()).isNull();
	}

}
