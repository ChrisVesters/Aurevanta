package eu.sonetas.aurevanta.ratelimit;

import eu.sonetas.aurevanta.TestcontainersConfiguration;
import eu.sonetas.aurevanta.auth.registration.RegistrationRequest;
import eu.sonetas.aurevanta.auth.signin.LoginRequest;
import eu.sonetas.aurevanta.auth.verification.VerifyEmailRequest;
import eu.sonetas.aurevanta.mail.RecordingEmailSender;
import eu.sonetas.aurevanta.mail.RecordingEmailSenderConfiguration;
import eu.sonetas.aurevanta.membership.MembershipRepository;
import eu.sonetas.aurevanta.tenant.TenantRepository;
import eu.sonetas.aurevanta.user.UserRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How fast a password can be guessed at.
 *
 * <p>
 * Sign-in needs no credentials to attempt, so without a limit the only cost of a guess is
 * how long bcrypt takes — a price per attempt, not a bound on how many. What the tests
 * here care about besides the bound itself is that it never becomes a way to keep
 * somebody out of their own account, and never becomes a way to ask which addresses are
 * registered.
 */
@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
		properties = { "aurevanta.rate-limit.sign-in-per-ip=3", "aurevanta.rate-limit.sign-in-per-account=5" })
class SignInRateLimitApiTests {

	private static final String PASSWORD = "correct-horse-battery";

	private static final String WRONG_PASSWORD = "not-the-right-password";

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
	private RecordingEmailSender mail;

	@Autowired
	private MailRateLimiter mailRateLimiter;

	@Autowired
	private SignInRateLimiter signInRateLimiter;

	@BeforeEach
	void clearAccountsAndCounts() {
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();
		this.mail.clear();
		this.mailRateLimiter.clear();
		this.signInRateLimiter.clear();
	}

	@Test
	void refusesFurtherGuessesFromOneSource() throws Exception {
		registerAndConfirm("ada@acme.test");
		for (int guess = 0; guess < 3; guess++) {
			signIn("ada@acme.test", WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		signIn("ada@acme.test", WRONG_PASSWORD).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("too_many_requests"))
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
	}

	/**
	 * Once the budget is spent the password is not looked at, right or wrong. That is
	 * what makes the limit worth having: an attacker who has run out cannot go on testing
	 * candidates, and cannot learn that one of them was correct. It also means the
	 * refusal costs no bcrypt, since nothing gets as far as checking.
	 */
	@Test
	void refusesEvenTheCorrectPasswordOnceTheBudgetIsSpent() throws Exception {
		registerAndConfirm("ada@acme.test");
		for (int guess = 0; guess < 6; guess++) {
			signIn("ada@acme.test", WRONG_PASSWORD);
		}

		signIn("ada@acme.test", PASSWORD).andExpect(status().isTooManyRequests());
	}

	/** Correct credentials are not something to slow down, however often they arrive. */
	@Test
	void neverCountsASuccess() throws Exception {
		registerAndConfirm("ada@acme.test");

		for (int attempt = 0; attempt < 6; attempt++) {
			signIn("ada@acme.test", PASSWORD).andExpect(status().isOk());
		}
	}

	/** Fumbling a passphrase and then getting it right must not leave a mark. */
	@Test
	void forgetsAnAccountsFailuresOnceSomebodySignsIn() throws Exception {
		registerAndConfirm("ada@acme.test");
		signIn("ada@acme.test", WRONG_PASSWORD).andExpect(status().isUnauthorized());
		signIn("ada@acme.test", WRONG_PASSWORD).andExpect(status().isUnauthorized());

		signIn("ada@acme.test", PASSWORD).andExpect(status().isOk());

		// The account's count is cleared, so there is a full allowance again.
		signIn("ada@acme.test", WRONG_PASSWORD).andExpect(status().isUnauthorized());
	}

	/**
	 * The reason the per-account limit sits several times above the per-source one. A
	 * per-account limit is also a way to lock somebody out of their own account, so one
	 * machine must not be able to fill it: this one exhausts its own allowance first, and
	 * the account holder signs in from anywhere else without noticing.
	 */
	@Test
	void cannotBeUsedToLockSomebodyOutFromASingleSource() throws Exception {
		registerAndConfirm("ada@acme.test");
		for (int guess = 0; guess < 10; guess++) {
			signIn("ada@acme.test", WRONG_PASSWORD);
		}

		this.mvc.perform(from(loginRequest("ada@acme.test", PASSWORD), "203.0.113.9")).andExpect(status().isOk());
	}

	/**
	 * Enough sources together do fill it, which is the distributed case it exists for.
	 */
	@Test
	void refusesAnAccountBeingGuessedAtFromEverywhere() throws Exception {
		registerAndConfirm("ada@acme.test");
		for (int source = 0; source < 5; source++) {
			this.mvc.perform(from(loginRequest("ada@acme.test", WRONG_PASSWORD), "203.0.113." + source))
				.andExpect(status().isUnauthorized());
		}

		this.mvc.perform(from(loginRequest("ada@acme.test", WRONG_PASSWORD), "203.0.113.99"))
			.andExpect(status().isTooManyRequests());
	}

	/**
	 * An address nobody has registered is counted exactly like one that exists. Anything
	 * else and the refusal would answer the question {@code invalid_credentials} is
	 * carefully worded to leave unanswered.
	 */
	@Test
	void treatsAnUnknownAddressLikeARegisteredOne() throws Exception {
		for (int guess = 0; guess < 3; guess++) {
			signIn("nobody@acme.test", WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		signIn("nobody@acme.test", WRONG_PASSWORD).andExpect(status().isTooManyRequests());
	}

	@Test
	void leavesAnotherSourceAlone() throws Exception {
		registerAndConfirm("ada@acme.test");
		for (int guess = 0; guess < 3; guess++) {
			signIn("ada@acme.test", WRONG_PASSWORD);
		}

		this.mvc.perform(from(loginRequest("ada@acme.test", WRONG_PASSWORD), "203.0.113.9"))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * Reaching the gate means the password was right, so it is not a guess — and counting
	 * it would throttle somebody out of the account they are trying to rescue, which is
	 * the one journey the gate depends on.
	 */
	@Test
	void doesNotCountBeingStoppedByTheVerificationGate() throws Exception {
		register("ada@acme.test");

		for (int attempt = 0; attempt < 6; attempt++) {
			signIn("ada@acme.test", PASSWORD).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("email_not_verified"));
		}
	}

	private void register(String email) throws Exception {
		String body = this.json.writeValueAsString(new RegistrationRequest("Acme", "Ada", email, PASSWORD));
		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
	}

	private void registerAndConfirm(String email) throws Exception {
		register(email);
		Matcher token = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(this.mail.lastMessage().body());
		assertThat(token.find()).as("a confirmation link in the message body").isTrue();
		String body = this.json.writeValueAsString(new VerifyEmailRequest(token.group(1)));
		this.mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNoContent());
		this.mail.clear();
	}

	private ResultActions signIn(String email, String password) throws Exception {
		return this.mvc.perform(loginRequest(email, password));
	}

	private MockHttpServletRequestBuilder loginRequest(String email, String password) throws Exception {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new LoginRequest(email, password)));
	}

	private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String sourceAddress) {
		return request.with((servletRequest) -> {
			servletRequest.setRemoteAddr(sourceAddress);
			return servletRequest;
		});
	}

}
