package com.cvesters.aurevanta.auth.verification;

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

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.auth.registration.RegistrationRequest;
import com.cvesters.aurevanta.auth.verification.ResendVerificationRequest;
import com.cvesters.aurevanta.auth.verification.VerifyEmailRequest;
import com.cvesters.aurevanta.mail.EmailMessage;
import com.cvesters.aurevanta.mail.RecordingEmailSender;
import com.cvesters.aurevanta.mail.RecordingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
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
 * Confirming an address, and asking for another link when the first never arrived.
 *
 * <p>
 * Both endpoints are reachable without credentials, which they have to be — the person
 * who needs them cannot sign in — so what matters as much as the happy path is that
 * neither one answers differently for an address that holds an account.
 */
@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class EmailVerificationApiTests {

	private static final String PASSWORD = "correct-horse-battery";

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

	@BeforeEach
	void clearAccounts() {
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();
		this.mail.clear();
		this.rateLimiter.clear();
	}

	@Test
	void registrationPutsAConfirmationLinkInThePost() throws Exception {
		register("ada@acme.test");

		EmailMessage sent = this.mail.onlyMessage();
		assertThat(sent.to()).isEqualTo("ada@acme.test");
		assertThat(sent.subject()).contains("Confirm");
		assertThat(sent.body()).contains("Ada").contains("/verify-email?token=");
	}

	@Test
	void followingTheLinkProvesTheAddress() throws Exception {
		register("ada@acme.test");

		verify(tokenFromLatestMail()).andExpect(status().isNoContent());

		assertThat(this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow().isEmailVerified()).isTrue();
	}

	/**
	 * A link that kept working would be a standing way in for anyone who saw the mail.
	 */
	@Test
	void followingTheSameLinkTwiceFails() throws Exception {
		register("ada@acme.test");
		String token = tokenFromLatestMail();
		verify(token).andExpect(status().isNoContent());

		verify(token).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_token"));
	}

	@Test
	void followingAnExpiredLinkFails() throws Exception {
		register("ada@acme.test");
		User ada = this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow();
		SingleUseToken stale = this.tokens.issue(ada, TokenPurpose.EMAIL_VERIFICATION, Duration.ofDays(-1));

		verify(stale.value()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_token"));

		assertThat(this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow().isEmailVerified()).isFalse();
	}

	@Test
	void followingALinkThatWasNeverIssuedFails() throws Exception {
		verify("not-a-token-we-issued").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("invalid_token"));
	}

	@Test
	void anotherLinkCanBeAskedForWhenTheFirstNeverArrived() throws Exception {
		register("ada@acme.test");
		String first = tokenFromLatestMail();
		this.mail.clear();

		resend("ada@acme.test").andExpect(status().isAccepted());

		String second = tokenFromLatestMail();
		assertThat(second).isNotEqualTo(first);
		verify(second).andExpect(status().isNoContent());
	}

	/**
	 * Nothing here may differ for an address that holds an account. This endpoint needs
	 * no credentials, so an answer that varied would let anyone ask who is registered.
	 */
	@Test
	void askingForALinkForAnUnknownAddressIsAcceptedAndSendsNothing() throws Exception {
		resend("nobody@acme.test").andExpect(status().isAccepted());

		assertThat(this.mail.sent()).isEmpty();
	}

	@Test
	void askingForALinkForAnAlreadyConfirmedAddressIsAcceptedAndSendsNothing() throws Exception {
		register("ada@acme.test");
		verify(tokenFromLatestMail()).andExpect(status().isNoContent());
		this.mail.clear();

		resend("ada@acme.test").andExpect(status().isAccepted());

		assertThat(this.mail.sent()).isEmpty();
	}

	/** The address is stripped and matched without regard to case, as everywhere else. */
	@Test
	void asksForALinkByAddressHoweverItIsSpelled() throws Exception {
		register("ada@acme.test");
		this.mail.clear();

		resend("  ADA@ACME.TEST  ").andExpect(status().isAccepted());

		assertThat(this.mail.sent()).hasSize(1);
	}

	@Test
	void rejectsARequestThatNamesNoToken() throws Exception {
		verify("  ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.token.code").value("not_blank"));
	}

	@Test
	void rejectsAResendRequestThatNamesNoAddress() throws Exception {
		resend("not-an-address").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email.code").value("email"));
	}

	private void register(String email) throws Exception {
		String body = this.json.writeValueAsString(new RegistrationRequest("Acme", "Ada", email, PASSWORD));
		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

	private org.springframework.test.web.servlet.ResultActions verify(String token) throws Exception {
		String body = this.json.writeValueAsString(new VerifyEmailRequest(token));
		return this.mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private org.springframework.test.web.servlet.ResultActions resend(String email) throws Exception {
		String body = this.json.writeValueAsString(new ResendVerificationRequest(email));
		return this.mvc
			.perform(post("/api/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String tokenFromLatestMail() {
		Matcher token = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(this.mail.lastMessage().body());
		assertThat(token.find()).as("a confirmation link in the message body").isTrue();
		return token.group(1);
	}

}
