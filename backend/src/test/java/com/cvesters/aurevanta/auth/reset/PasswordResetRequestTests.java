package com.cvesters.aurevanta.auth.reset;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.auth.reset.ConfirmPasswordResetRequest;
import com.cvesters.aurevanta.auth.reset.PasswordResetRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both records strip before validation for the reason given on
 * {@code RegistrationRequest} — with the password as the deliberate exception, since
 * trimming it would store one credential and compare another.
 */
class PasswordResetRequestTests {

	@Test
	void stripsAnAddressPastedWithSurroundingSpace() {
		assertThat(new PasswordResetRequest("  ada@acme.test  ").email()).isEqualTo("ada@acme.test");
	}

	@Test
	void stripsATokenCopiedWithSurroundingSpace() {
		assertThat(new ConfirmPasswordResetRequest("  a-token  ", "a-passphrase").token()).isEqualTo("a-token");
	}

	/**
	 * Spaces are legitimate in a passphrase, including at either end. Trimming would set
	 * a password the account holder could then never type.
	 */
	@Test
	void leavesThePasswordExactlyAsTyped() {
		assertThat(new ConfirmPasswordResetRequest("a-token", "  spaced passphrase  ").password())
			.isEqualTo("  spaced passphrase  ");
	}

	/** A missing value has to survive as far as validation, which is what reports it. */
	@Test
	void tolerateMissingValues() {
		assertThat(new PasswordResetRequest(null).email()).isNull();
		assertThat(new ConfirmPasswordResetRequest(null, null).token()).isNull();
		assertThat(new ConfirmPasswordResetRequest(null, null).password()).isNull();
	}

}
