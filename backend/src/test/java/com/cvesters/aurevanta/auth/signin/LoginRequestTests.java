package com.cvesters.aurevanta.auth.signin;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.auth.signin.LoginRequest;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRequestTests {

	@Test
	void stripsTheAddressBeforeValidationSeesIt() {
		assertThat(new LoginRequest("  ada@acme.test  ", "secret").email()).isEqualTo("ada@acme.test");
	}

	/** Or sign-in would compare something other than what registration stored. */
	@Test
	void leavesThePasswordExactlyAsItWasTyped() {
		assertThat(new LoginRequest("ada@acme.test", "  pass phrase  ").password()).isEqualTo("  pass phrase  ");
	}

	/** A missing field has to survive as far as validation, which is what reports it. */
	@Test
	void toleratesMissingValues() {
		LoginRequest request = new LoginRequest(null, null);

		assertThat(request.email()).isNull();
		assertThat(request.password()).isNull();
	}

}
