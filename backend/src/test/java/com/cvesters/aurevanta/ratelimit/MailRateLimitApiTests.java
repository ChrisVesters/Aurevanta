package com.cvesters.aurevanta.ratelimit;

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

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.auth.registration.RegistrationRequest;
import com.cvesters.aurevanta.mail.RecordingEmailSender;
import com.cvesters.aurevanta.mail.RecordingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The limit as an endpoint actually applies it.
 *
 * <p>
 * Every endpoint here needs no credentials and sends mail to an address the caller chose,
 * which is what makes the limit load-bearing rather than hardening: without it, anybody
 * can point this application at a stranger's inbox and hold the button down.
 *
 * <p>
 * Limits are dropped to something a test can reach; the shape of the rule is what is
 * being proved, not the numbers configured for production.
 */
@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = { "aurevanta.rate-limit.per-address=2", "aurevanta.rate-limit.per-ip=5" })
class MailRateLimitApiTests {

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
	private RecordingEmailSender mail;

	@Autowired
	private MailRateLimiter rateLimiter;

	@BeforeEach
	void clearAccountsAndCounts() {
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();
		this.mail.clear();
		this.rateLimiter.clear();
	}

	@Test
	void refusesTheRequestAfterTheLimitWithItsOwnCode() throws Exception {
		requestReset("ada@acme.test").andExpect(status().isAccepted());
		requestReset("ada@acme.test").andExpect(status().isAccepted());

		requestReset("ada@acme.test").andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("too_many_requests"));
	}

	/** A refusal a client cannot act on is a refusal it will retry against in a loop. */
	@Test
	void saysHowLongToWait() throws Exception {
		requestReset("ada@acme.test");
		requestReset("ada@acme.test");

		String retryAfter = requestReset("ada@acme.test").andExpect(status().isTooManyRequests())
			.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.RETRY_AFTER);

		// Not the window exactly: real time passes between the first request and this
		// one,
		// and what is promised is when a slot frees up rather than a round number. The
		// arithmetic is pinned against a clock a test can move, over in RateLimiterTests.
		assertThat(Long.parseLong(retryAfter)).isPositive().isLessThanOrEqualTo(900);
	}

	/** The whole point: the message that would have been sent is not sent. */
	@Test
	void sendsNothingForARefusedRequest() throws Exception {
		registerAndConfirmNothing("ada@acme.test");
		requestReset("ada@acme.test");
		this.mail.clear();

		requestReset("ada@acme.test").andExpect(status().isTooManyRequests());

		assertThat(this.mail.sent()).isEmpty();
	}

	@Test
	void leavesAnotherAddressAlone() throws Exception {
		requestReset("ada@acme.test");
		requestReset("ada@acme.test");

		requestReset("grace@acme.test").andExpect(status().isAccepted());
	}

	/**
	 * One budget per recipient rather than one per endpoint. Two endpoints that both
	 * write to the same inbox would otherwise hand an attacker twice the allowance simply
	 * for alternating between them, and the person being written to cannot tell the
	 * difference.
	 */
	@Test
	void countsEveryRouteToOneInboxTogether() throws Exception {
		requestReset("ada@acme.test").andExpect(status().isAccepted());
		resendConfirmation("ada@acme.test").andExpect(status().isAccepted());

		requestReset("ada@acme.test").andExpect(status().isTooManyRequests());
	}

	/** The address is one mailbox however it is spelled, so the count has to be too. */
	@Test
	void countsAnAddressHoweverItIsSpelled() throws Exception {
		requestReset("ada@acme.test").andExpect(status().isAccepted());
		requestReset("  ADA@ACME.TEST  ").andExpect(status().isAccepted());

		requestReset("Ada@Acme.Test").andExpect(status().isTooManyRequests());
	}

	/**
	 * What the per-address limit cannot see: one message each to a great many addresses,
	 * none of which is a second message to anybody.
	 */
	@Test
	void refusesOneSourceSprayingManyAddresses() throws Exception {
		for (int address = 0; address < 5; address++) {
			requestReset("nobody-" + address + "@acme.test").andExpect(status().isAccepted());
		}

		requestReset("nobody-5@acme.test").andExpect(status().isTooManyRequests());
	}

	@Test
	void leavesAnotherSourceAlone() throws Exception {
		for (int address = 0; address < 5; address++) {
			requestReset("nobody-" + address + "@acme.test").andExpect(status().isAccepted());
		}

		this.mvc.perform(from(resetRequest("someone@acme.test"), "203.0.113.9")).andExpect(status().isAccepted());
	}

	/**
	 * Registering can only ever send one message to any given address, so it is not a way
	 * to bury somebody — but it is a way to send a stranger's inbox one message it never
	 * asked for, repeated across as many strangers as somebody cares to type.
	 */
	@Test
	void limitsRegistrationBySourceToo() throws Exception {
		for (int account = 0; account < 5; account++) {
			register("acme-" + account, "nobody-" + account + "@acme.test").andExpect(status().isCreated());
		}

		register("acme-5", "nobody-5@acme.test").andExpect(status().isTooManyRequests());
	}

	/**
	 * Counted before anything is looked up. A limit that only counted the addresses that
	 * turned out to have accounts would answer, through its own refusals, exactly the
	 * question the blanket {@code 202} exists to refuse.
	 */
	@Test
	void countsAnUnknownAddressLikeAnyOther() throws Exception {
		requestReset("nobody@acme.test").andExpect(status().isAccepted());
		requestReset("nobody@acme.test").andExpect(status().isAccepted());

		requestReset("nobody@acme.test").andExpect(status().isTooManyRequests());
		assertThat(this.mail.sent()).isEmpty();
	}

	/**
	 * A handle somebody else holds is a refusal the product invites people to retry — the
	 * form fills in the alternative it carries. Spending the inbox's budget on it would
	 * mean three collisions locked somebody out of registering at all, and out of the
	 * password reset that shares the budget, for a quarter of an hour.
	 */
	@Test
	void aTakenHandleDoesNotSpendTheRecipientsBudget() throws Exception {
		register("acme", "ada@acme.test").andExpect(status().isCreated());

		for (int attempt = 0; attempt < 3; attempt++) {
			register("acme", "grace@acme.test").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("slug_taken"));
		}

		register("umbrella", "grace@acme.test").andExpect(status().isCreated());
	}

	/**
	 * The source keeps its claim, because it spent this application's time either way: a
	 * refused registration still costs a lookup and a bcrypt hash, and refunding that
	 * would leave an endpoint that hashes passwords unlimited to anybody willing to
	 * collide on a handle every time.
	 */
	@Test
	void aTakenHandleStillSpendsTheSourcesBudget() throws Exception {
		register("acme", "ada@acme.test").andExpect(status().isCreated());

		for (int attempt = 0; attempt < 4; attempt++) {
			register("acme", "nobody-" + attempt + "@acme.test").andExpect(status().isConflict());
		}

		register("umbrella", "grace@acme.test").andExpect(status().isTooManyRequests());
	}

	private ResultActions requestReset(String email) throws Exception {
		return this.mvc.perform(resetRequest(email));
	}

	private MockHttpServletRequestBuilder resetRequest(String email) throws Exception {
		return post("/api/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new EmailBody(email)));
	}

	private ResultActions resendConfirmation(String email) throws Exception {
		return this.mvc.perform(post("/api/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new EmailBody(email))));
	}

	private ResultActions register(String organisation, String email) throws Exception {
		return this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
			.content(this.json
				.writeValueAsString(new RegistrationRequest(organisation, organisation, "Ada", email, PASSWORD))));
	}

	/** Registers an account so a reset has somebody to find, then forgets the mail. */
	private void registerAndConfirmNothing(String email) throws Exception {
		register("acme", email).andExpect(status().isCreated());
		this.mail.clear();
	}

	/** Same request, arriving from somewhere else. */
	private static MockHttpServletRequestBuilder from(MockHttpServletRequestBuilder request, String sourceAddress) {
		return request.with((servletRequest) -> {
			servletRequest.setRemoteAddr(sourceAddress);
			return servletRequest;
		});
	}

	/**
	 * Both endpoints take the same one-field body; this saves importing either record.
	 */
	private record EmailBody(String email) {
	}

}
