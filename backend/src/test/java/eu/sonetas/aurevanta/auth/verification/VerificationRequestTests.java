package eu.sonetas.aurevanta.auth.verification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both records strip before validation for the reason given on
 * {@code RegistrationRequest} — a token copied out of a mail client, or an address pasted
 * from anywhere, arrives with whitespace round it more often than not.
 */
class VerificationRequestTests {

	@Test
	void stripsATokenCopiedWithSurroundingSpace() {
		assertThat(new VerifyEmailRequest("  a-token  ").token()).isEqualTo("a-token");
	}

	@Test
	void stripsAnAddressPastedWithSurroundingSpace() {
		assertThat(new ResendVerificationRequest("  ada@acme.test  ").email()).isEqualTo("ada@acme.test");
	}

	/** A missing value has to survive as far as validation, which is what reports it. */
	@Test
	void tolerateMissingValues() {
		assertThat(new VerifyEmailRequest(null).token()).isNull();
		assertThat(new ResendVerificationRequest(null).email()).isNull();
	}

}
