package com.cvesters.aurevanta.auth.reset;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.auth.registration.RegistrationRequest;
import com.cvesters.aurevanta.auth.reset.ConfirmPasswordResetRequest;
import com.cvesters.aurevanta.auth.reset.PasswordResetRequest;
import com.cvesters.aurevanta.auth.signin.LoginRequest;
import com.cvesters.aurevanta.auth.verification.VerifyEmailRequest;
import com.cvesters.aurevanta.mail.EmailMessage;
import com.cvesters.aurevanta.mail.RecordingEmailSender;
import com.cvesters.aurevanta.mail.RecordingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.ratelimit.SignInRateLimiter;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.token.SingleUseToken;
import com.cvesters.aurevanta.token.SingleUseTokenService;
import com.cvesters.aurevanta.token.TokenPurpose;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asking for a new password, and setting one.
 *
 * <p>
 * Both endpoints are reachable without credentials, which they have to be — somebody who
 * has lost their password cannot sign in to ask for another. So what matters as much as
 * the happy path is that asking never reveals which addresses hold accounts, and that a
 * link is worth exactly one password change.
 */
@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetApiTests {

	private static final String OLD_PASSWORD = "correct-horse-battery";

	private static final String NEW_PASSWORD = "a-quite-different-passphrase";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private SingleUseTokenService tokens;

	@Autowired
	private RecordingEmailSender mail;

	/**
	 * Shared across every case in this class, and every request here provokes mail, so
	 * without this one case would spend the allowance the next one needs.
	 */
	@Autowired
	private MailRateLimiter rateLimiter;

	@Autowired
	private SignInRateLimiter signInRateLimiter;

	@BeforeEach
	void clearAccounts() {
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();
		this.mail.clear();
		this.rateLimiter.clear();
		this.signInRateLimiter.clear();
	}

	@Test
	void askingForAResetPutsALinkInThePost() throws Exception {
		registerAndConfirm("ada@acme.test");

		requestReset("ada@acme.test").andExpect(status().isAccepted());

		EmailMessage sent = this.mail.onlyMessage();
		assertThat(sent.to()).isEqualTo("ada@acme.test");
		assertThat(sent.subject()).contains("password");
		assertThat(sent.body()).contains("Ada").contains("/reset-password?token=").contains("1 hour");
	}

	/** The whole point: the credential actually changes, in both directions. */
	@Test
	void theNewPasswordWorksAndTheOldOneStopsWorking() throws Exception {
		registerAndConfirm("ada@acme.test");
		requestReset("ada@acme.test");

		confirmReset(tokenFromLatestMail(), NEW_PASSWORD).andExpect(status().isNoContent());

		signIn("ada@acme.test", OLD_PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
		signIn("ada@acme.test", NEW_PASSWORD).andExpect(status().isOk());
	}

	/**
	 * The reason this step exists at all. An address that was never confirmed cannot sign
	 * in, so without this the account would be unreachable for ever — and following the
	 * reset link proves the address just as a confirmation link does.
	 */
	@Test
	void recoveringAnAccountConfirmsAnAddressThatWasNeverConfirmed() throws Exception {
		register("ada@acme.test");
		assertThat(this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow().isEmailVerified()).isFalse();
		this.mail.clear();

		requestReset("ada@acme.test").andExpect(status().isAccepted());
		confirmReset(tokenFromLatestMail(), NEW_PASSWORD).andExpect(status().isNoContent());

		assertThat(this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow().isEmailVerified()).isTrue();
		signIn("ada@acme.test", NEW_PASSWORD).andExpect(status().isOk());
	}

	/**
	 * Nothing here may differ for an address that holds an account, or this endpoint
	 * becomes a way for anyone to ask who is registered.
	 */
	@Test
	void askingForAResetForAnUnknownAddressIsAcceptedAndSendsNothing() throws Exception {
		requestReset("nobody@acme.test").andExpect(status().isAccepted());

		assertThat(this.mail.sent()).isEmpty();
	}

	@Test
	void asksForAResetByAddressHoweverItIsSpelled() throws Exception {
		registerAndConfirm("ada@acme.test");

		requestReset("  ADA@ACME.TEST  ").andExpect(status().isAccepted());

		assertThat(this.mail.sent()).hasSize(1);
	}

	/** A link that kept working would be a standing way into the account. */
	@Test
	void followingTheSameLinkTwiceFails() throws Exception {
		registerAndConfirm("ada@acme.test");
		requestReset("ada@acme.test");
		String token = tokenFromLatestMail();
		confirmReset(token, NEW_PASSWORD).andExpect(status().isNoContent());

		confirmReset(token, "yet-another-passphrase").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));

		signIn("ada@acme.test", NEW_PASSWORD).andExpect(status().isOk());
	}

	/**
	 * Asking twice leaves two live links in an inbox. Once one has been used the other is
	 * no longer something the owner needs, and leaving it working means a message read by
	 * anybody else later is still a way in.
	 */
	@Test
	void usingOneLinkSpendsEveryOtherLinkTheAccountHolds() throws Exception {
		registerAndConfirm("ada@acme.test");
		requestReset("ada@acme.test");
		String first = tokenFromLatestMail();
		requestReset("ada@acme.test");
		String second = tokenFromLatestMail();
		assertThat(second).isNotEqualTo(first);

		confirmReset(second, NEW_PASSWORD).andExpect(status().isNoContent());

		confirmReset(first, "yet-another-passphrase").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
		signIn("ada@acme.test", NEW_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void followingAnExpiredLinkFails() throws Exception {
		registerAndConfirm("ada@acme.test");
		User ada = this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow();
		SingleUseToken stale = this.tokens.issue(ada, TokenPurpose.PASSWORD_RESET, Duration.ofHours(-1));

		confirmReset(stale.value(), NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));

		signIn("ada@acme.test", OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void followingALinkThatWasNeverIssuedFails() throws Exception {
		confirmReset("not-a-token-we-issued", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
	}

	/**
	 * A confirmation link proves somebody reads an inbox; a reset link lets them take the
	 * account over. Spending the weaker one here would erase that difference — and every
	 * registration puts a confirmation link in the post, so this is not a hypothetical
	 * token to come by.
	 */
	@Test
	void refusesAConfirmationLinkAsAPasswordReset() throws Exception {
		register("ada@acme.test");
		String confirmation = tokenFromLatestMail();

		confirmReset(confirmation, NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));

		// Refused without being spent, so it still confirms the address it was issued
		// for.
		confirmAddress(confirmation).andExpect(status().isNoContent());
		signIn("ada@acme.test", OLD_PASSWORD).andExpect(status().isOk());
	}

	@Test
	void rejectsARequestThatNamesNoAddress() throws Exception {
		requestReset("not-an-address").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email.code").value("email"));
	}

	@Test
	void rejectsAConfirmationThatNamesNoToken() throws Exception {
		confirmReset("  ", NEW_PASSWORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.token.code").value("not_blank"));
	}

	/**
	 * The bound registration applies, applied here too — otherwise this endpoint would
	 * set a password that could not have been registered, which is the rule rewritten by
	 * its weakest caller. The link survives, since nothing reached the service to spend
	 * it.
	 */
	@Test
	void rejectsANewPasswordTooShortToHaveBeenRegistered() throws Exception {
		registerAndConfirm("ada@acme.test");
		requestReset("ada@acme.test");
		String token = tokenFromLatestMail();

		confirmReset(token, "too-short").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.password.code").value("size"))
			.andExpect(jsonPath("$.errors.password.min").value(12))
			.andExpect(jsonPath("$.errors.password.max").value(72));

		confirmReset(token, NEW_PASSWORD).andExpect(status().isNoContent());
	}

	private void register(String email) throws Exception {
		String body = this.json.writeValueAsString(new RegistrationRequest("Acme", "acme", "Ada", email, OLD_PASSWORD));
		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

	/**
	 * Registers and follows the confirmation link, leaving an account that can sign in.
	 */
	private void registerAndConfirm(String email) throws Exception {
		register(email);
		confirmAddress(tokenFromLatestMail()).andExpect(status().isNoContent());
		this.mail.clear();
	}

	private ResultActions confirmAddress(String token) throws Exception {
		String body = this.json.writeValueAsString(new VerifyEmailRequest(token));
		return this.mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions requestReset(String email) throws Exception {
		String body = this.json.writeValueAsString(new PasswordResetRequest(email));
		return this.mvc.perform(post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions confirmReset(String token, String password) throws Exception {
		String body = this.json.writeValueAsString(new ConfirmPasswordResetRequest(token, password));
		return this.mvc
			.perform(post("/api/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions signIn(String email, String password) throws Exception {
		String body = this.json.writeValueAsString(new LoginRequest(email, password));
		return this.mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String tokenFromLatestMail() {
		Matcher token = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(this.mail.lastMessage().body());
		assertThat(token.find()).as("a link in the message body").isTrue();
		return token.group(1);
	}

}
