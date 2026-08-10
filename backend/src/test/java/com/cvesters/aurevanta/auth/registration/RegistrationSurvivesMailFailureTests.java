package com.cvesters.aurevanta.auth.registration;

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
import com.cvesters.aurevanta.mail.FailingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A mail server that is down must not turn a good registration into a failed one.
 *
 * <p>
 * Run against the real asynchronous wrapper rather than a recording double, because the
 * wrapper is the thing that makes this true — a test that replaced the whole port would
 * pass without exercising it at all. The account is created either way; what the person
 * has lost is the link, which they can ask for again.
 */
@Import({ TestcontainersConfiguration.class, FailingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationSurvivesMailFailureTests {

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
		this.rateLimiter.clear();
	}

	@Test
	void registrationSucceedsWhenTheMailServerRefusesTheMessage() throws Exception {
		String body = this.json
			.writeValueAsString(new RegistrationRequest("Acme", "Ada", "ada@acme.test", "correct-horse-battery"));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());

		// The account is real, and still unusable until a link arrives — which is a thing
		// to ask for again, not a reason to have refused the registration.
		assertThat(this.users.findByEmailIgnoringCase("ada@acme.test")).isPresent();
	}

}
