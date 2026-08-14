package com.cvesters.aurevanta.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.auth.registration.RegistrationRequest;
import com.cvesters.aurevanta.auth.signin.LoginRequest;
import com.cvesters.aurevanta.auth.verification.VerifyEmailRequest;
import com.cvesters.aurevanta.mail.RecordingEmailSender;
import com.cvesters.aurevanta.mail.RecordingEmailSenderConfiguration;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.ratelimit.MailRateLimiter;
import com.cvesters.aurevanta.ratelimit.SignInRateLimiter;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({ TestcontainersConfiguration.class, RecordingEmailSenderConfiguration.class })
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiTests {

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
	void registrationCreatesAnOrganisationOwnedByTheRegisteringUser() throws Exception {
		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Acme Planning Co", "acme-planning-co", "ada@acme.test")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("ada@acme.test"))
			.andExpect(jsonPath("$.displayName").value("Ada"))
			.andExpect(jsonPath("$.role").value("OWNER"))
			.andExpect(jsonPath("$.organisation.name").value("Acme Planning Co"))
			.andExpect(jsonPath("$.organisation.slug").value("acme-planning-co"))
			.andExpect(jsonPath("$.organisation.id").isNotEmpty())
			// The address has not been proved yet, so there is nothing to sign in with.
			.andExpect(jsonPath("$.emailVerified").value(false))
			.andExpect(jsonPath("$.accessToken").doesNotExist())
			.andExpect(jsonPath("$.tokenType").doesNotExist());
	}

	@Test
	void registrationStoresThePasswordOnlyAsAHash() throws Exception {
		register("Acme", "acme", "ada@acme.test");

		User stored = this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow();

		assertThat(stored.getPasswordHash()).doesNotContain(PASSWORD).startsWith("{bcrypt}$2");
	}

	/** The account is the identity; the organisation and role hang off a membership. */
	@Test
	void registrationRecordsTheOwnerAsAMemberOfTheOrganisationItCreated() throws Exception {
		register("Acme", "acme", "ada@acme.test");

		User stored = this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow();

		assertThat(this.memberships.findAllForUser(stored.getId())).singleElement().satisfies((membership) -> {
			assertThat(membership.getRole()).isEqualTo(UserRole.OWNER);
			assertThat(membership.getTenant().getSlug()).isEqualTo("acme");
		});
	}

	/**
	 * Addresses arrive padded all the time — pasted out of a password manager or an email
	 * client. Stripping has to happen before validation, because {@code @Email} rejects a
	 * padded address outright, which would answer a perfectly good address with "enter a
	 * valid email address".
	 */
	@Test
	void registrationAcceptsAnAddressPastedWithSurroundingSpace() throws Exception {
		String body = this.json.writeValueAsString(
				new RegistrationRequest("  Acme  ", "  acme  ", "  Ada  ", "  ada@acme.test  ", PASSWORD));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("ada@acme.test"))
			.andExpect(jsonPath("$.displayName").value("Ada"))
			.andExpect(jsonPath("$.organisation.name").value("Acme"));

		assertThat(this.users.findByEmailIgnoringCase("ada@acme.test")).isPresent();
	}

	@Test
	void loginAcceptsAnAddressPastedWithSurroundingSpace() throws Exception {
		register("Acme", "acme", "ada@acme.test");
		confirmTheAddress();

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("  ada@acme.test  ", PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("SIGNED_IN"));
	}

	/**
	 * Spaces are legitimate characters in a passphrase. Stripping one would store a
	 * different credential than the visitor chose, and then refuse the one they type.
	 */
	@Test
	void registrationKeepsAPasswordExactlyAsItWasTyped() throws Exception {
		String padded = "  a passphrase with spaces  ";
		String body = this.json
			.writeValueAsString(new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", padded));
		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isCreated());
		confirmTheAddress();

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", padded)))
			.andExpect(status().isOk());
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", padded.strip())))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void eachRegistrationGetsItsOwnTenant() throws Exception {
		String first = organisationId(register("Acme", "acme", "ada@acme.test"));
		String second = organisationId(register("Umbrella", "umbrella", "grace@umbrella.test"));

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void registrationRejectsAnEmailThatDiffersOnlyByCase() throws Exception {
		register("Acme", "acme", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Umbrella", "umbrella", "ADA@Acme.test")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("email_already_registered"));
	}

	/**
	 * The whole point of M1a. There are thousands of Acme Consultings and nothing about
	 * this product makes the first to arrive the owner of the name.
	 */
	@Test
	void registrationAcceptsAnOrganisationNameSomebodyElseAlreadyUses() throws Exception {
		register("Acme Planning Co", "acme-planning-co", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Acme Planning Co", "acme-planning-co-2", "grace@acme.test")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.organisation.name").value("Acme Planning Co"))
			.andExpect(jsonPath("$.organisation.slug").value("acme-planning-co-2"));
	}

	/** The handle is refused, and the refusal arrives holding a way past itself. */
	@Test
	void registrationRefusesAHandleSomebodyElseHasAndOffersOneTheyDoNot() throws Exception {
		register("Acme Planning Co", "acme-planning-co", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Acme Planning Co", "acme-planning-co", "grace@acme.test")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("slug_taken"))
			.andExpect(jsonPath("$.suggested").value("acme-planning-co-2"));
	}

	/**
	 * A name with nothing to build a handle from used to be refused outright. Nothing is
	 * derived from it any more, so it is simply a name.
	 */
	@Test
	void registrationAcceptsAnOrganisationNameNoHandleCouldBeBuiltFrom() throws Exception {
		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("!!! ???", "acme", "ada@acme.test")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.organisation.name").value("!!! ???"))
			.andExpect(jsonPath("$.organisation.slug").value("acme"));
	}

	@Test
	void registrationRejectsAHandleThatIsNotTheShapeAHandleMustBe() throws Exception {
		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Acme", "Acme Planning Co", "ada@acme.test")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.organisationSlug.code").value("pattern"));
	}

	/**
	 * The bounds travel with the failure so the client can write its own sentence around
	 * them, rather than either hard-coding 12 or repeating the server's English.
	 */
	@Test
	void registrationRejectsAShortPasswordWithTheConstraintAndItsBounds() throws Exception {
		String body = this.json
			.writeValueAsString(new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", "short"));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.password.code").value("size"))
			.andExpect(jsonPath("$.errors.password.min").value(12))
			.andExpect(jsonPath("$.errors.password.max").value(72));
	}

	/**
	 * An empty password breaks both {@code @NotBlank} and {@code @Size}, and Hibernate
	 * Validator returns the two in a set whose order varies from one request to the next.
	 * Repeated here because a single call would pass roughly half the time whether or not
	 * the answer is actually pinned down.
	 */
	@Test
	void registrationGivesAnEmptyPasswordTheSameAnswerEveryTime() throws Exception {
		String body = this.json.writeValueAsString(new RegistrationRequest("Acme", "acme", "Ada", "ada@acme.test", ""));

		for (int attempt = 0; attempt < 12; attempt++) {
			this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.password.code").value("not_blank"));
		}
	}

	/**
	 * {@code @Size(max = 200)} reports a lower bound of zero, which would otherwise
	 * become "use between 0 and 200 characters" — a range nobody should be shown. A
	 * constraint that only bounds length above says so with its own code.
	 */
	@Test
	void registrationReportsALengthCeilingWithoutInventingAFloor() throws Exception {
		String tooLong = "x".repeat(201);
		String body = this.json
			.writeValueAsString(new RegistrationRequest(tooLong, "acme", "Ada", "ada@acme.test", PASSWORD));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.organisationName.code").value("max_size"))
			.andExpect(jsonPath("$.errors.organisationName.max").value(200))
			.andExpect(jsonPath("$.errors.organisationName.min").value(0));
	}

	@Test
	void registrationRejectsAMalformedEmailByConstraint() throws Exception {
		String body = this.json
			.writeValueAsString(new RegistrationRequest("Acme", "acme", "Ada", "not-an-address", PASSWORD));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email.code").value("email"));
	}

	@Test
	void registrationRejectsAMissingFieldByConstraint() throws Exception {
		String body = this.json
			.writeValueAsString(new RegistrationRequest("  ", "acme", "Ada", "ada@acme.test", PASSWORD));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.organisationName.code").value("not_blank"));
	}

	/**
	 * The whole point of the change: what a field failed is machine-readable, and the
	 * English Bean Validation generated for it never leaves the server. Publishing the
	 * regular expression or the message template would put implementation detail in front
	 * of a user in a language we did not choose.
	 */
	@Test
	void registrationSendsNoServerProseOrImplementationDetailWithAFieldError() throws Exception {
		String body = this.json
			.writeValueAsString(new RegistrationRequest("Acme", "acme", "Ada", "not-an-address", "short"));

		String response = this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			// The constraint's message template, its regular expression and its groups
			// stay
			// on the server; only what a sentence can be built around goes out.
			.andExpect(jsonPath("$.errors.password.message").doesNotExist())
			.andExpect(jsonPath("$.errors.password.groups").doesNotExist())
			.andExpect(jsonPath("$.errors.password.payload").doesNotExist())
			.andExpect(jsonPath("$.errors.email.regexp").doesNotExist())
			.andExpect(jsonPath("$.errors.email.flags").doesNotExist())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(response).doesNotContain("must be a well-formed").doesNotContain("size must be between");
	}

	/**
	 * One membership is the only state registration can produce, so the common path signs
	 * the caller straight in with nothing to choose between.
	 */
	@Test
	void loginSignsStraightInWhenTheAccountHoldsOneMembership() throws Exception {
		register("Acme", "acme", "ada@acme.test");
		confirmTheAddress();

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ADA@ACME.TEST", PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("SIGNED_IN"))
			.andExpect(jsonPath("$.session.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.session.account.organisation.slug").value("acme"))
			.andExpect(jsonPath("$.identity").doesNotExist());
	}

	/**
	 * Signing in validates its request the same way registering does, so the form has
	 * something to put against the offending input rather than only a banner saying that
	 * some field, somewhere, needs attention.
	 */
	@Test
	void loginRejectsAnEmptyFieldByConstraint() throws Exception {
		this.mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login("", "")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.email.code").value("not_blank"))
			.andExpect(jsonPath("$.errors.password.code").value("not_blank"));
	}

	/**
	 * Checking the shape of the address is not the same as checking who holds it. A typo
	 * that could not be anyone's address is worth saying so about — it leaks nothing,
	 * because the caller already knows what they typed — and it saves someone hunting for
	 * a password problem that does not exist.
	 */
	@Test
	void loginRejectsAMalformedEmailByConstraint() throws Exception {
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("not-an-address", PASSWORD)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.email.code").value("email"));
	}

	@Test
	void loginRejectsAWrongPassword() throws Exception {
		register("Acme", "acme", "ada@acme.test");
		confirmTheAddress();

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", "not-the-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	/**
	 * The gate. An account exists, the password is right, and it still cannot be used.
	 */
	@Test
	void loginRefusesAnAccountWhoseAddressWasNeverConfirmed() throws Exception {
		register("Acme", "acme", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", PASSWORD)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("email_not_verified"))
			.andExpect(jsonPath("$.session").doesNotExist())
			.andExpect(jsonPath("$.identity").doesNotExist());
	}

	/**
	 * The distinct answer is only safe once the password has proved the caller holds the
	 * account. A wrong password must stay indistinguishable from an address nobody has
	 * registered, or sign-in becomes a way to ask who is signed up here.
	 */
	@Test
	void loginHidesAnUnconfirmedAccountFromSomeoneWithTheWrongPassword() throws Exception {
		register("Acme", "acme", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", "not-the-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));

		// Byte for byte what an address with no account at all is told.
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("nobody@acme.test", "not-the-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	@Test
	void loginSucceedsOnceTheAddressIsConfirmed() throws Exception {
		register("Acme", "acme", "ada@acme.test");
		confirmTheAddress();

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("SIGNED_IN"))
			.andExpect(jsonPath("$.session.account.emailVerified").value(true));
	}

	@Test
	void loginReportsAnUnknownEmailExactlyLikeAWrongPassword() throws Exception {
		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("nobody@acme.test", PASSWORD)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	@Test
	void currentAccountIsReturnedForAValidToken() throws Exception {
		String token = registerAndSignIn("Acme Planning Co", "acme-planning-co", "ada@acme.test");

		this.mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("ada@acme.test"))
			.andExpect(jsonPath("$.role").value("OWNER"))
			.andExpect(jsonPath("$.organisation.slug").value("acme-planning-co"));
	}

	@Test
	void currentAccountRequiresAToken() throws Exception {
		this.mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}

	@Test
	void currentAccountRejectsATokenThatIsNotOurs() throws Exception {
		this.mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.token"))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * A token whose claims are entirely valid but whose signature is not ours. Proves the
	 * signature is actually checked, rather than the earlier garbage-token test merely
	 * failing to parse.
	 */
	@Test
	void currentAccountRejectsATokenSignedWithAnotherKey() throws Exception {
		SecretKey foreignKey = new SecretKeySpec("a-forgers-secret-key-of-good-length".getBytes(StandardCharsets.UTF_8),
				"HmacSHA256");
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("aurevanta")
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plus(Duration.ofHours(1)))
			.claim("token_type", "access")
			.claim("tenant_id", UUID.randomUUID().toString())
			.claim("email", "forger@acme.test")
			.claim("role", "OWNER")
			.build();
		String forged = new NimbusJwtEncoder(new ImmutableSecret<>(foreignKey))
			.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		this.mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void aTokenStopsWorkingOnceItsAccountIsGone() throws Exception {
		String token = registerAndSignIn("Acme", "acme", "ada@acme.test");
		this.memberships.deleteAll();
		this.users.deleteAll();

		this.mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * The token still verifies and still names a tenant, but the standing it describes is
	 * gone. Reloading the membership on every request is what makes that take effect at
	 * once rather than when the token happens to expire.
	 */
	@Test
	void aTokenStopsWorkingOnceItsMembershipIsRevoked() throws Exception {
		String token = registerAndSignIn("Acme", "acme", "ada@acme.test");
		this.memberships.deleteAll();

		this.mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	private MvcResult register(String organisation, String slug, String email) throws Exception {
		return this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration(organisation, slug, email)))
			.andExpect(status().isCreated())
			.andReturn();
	}

	/**
	 * Name and handle are passed separately because they are no longer derived from one
	 * another: the caller chooses the handle, and a test that let one stand for both
	 * could not express the case this milestone exists for.
	 */
	private String registration(String organisation, String slug, String email) throws Exception {
		return this.json.writeValueAsString(new RegistrationRequest(organisation, slug, "Ada", email, PASSWORD));
	}

	private String login(String email, String password) throws Exception {
		return this.json.writeValueAsString(new LoginRequest(email, password));
	}

	/**
	 * Registers, follows the confirmation link, and signs in — the whole journey a new
	 * account now has to make before it can hold a token.
	 */
	private String registerAndSignIn(String organisation, String slug, String email) throws Exception {
		register(organisation, slug, email);
		confirmTheAddress();
		return body(this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login(email, PASSWORD)))
			.andExpect(status().isOk())
			.andReturn()).get("session").get("accessToken").asString();
	}

	/** Follows the link out of the message registration just sent. */
	private void confirmTheAddress() throws Exception {
		String body = this.json.writeValueAsString(new VerifyEmailRequest(tokenFromLatestMail()));
		this.mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isNoContent());
	}

	private String tokenFromLatestMail() {
		Matcher token = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(this.mail.lastMessage().body());
		assertThat(token.find()).as("a confirmation link in the message body").isTrue();
		return token.group(1);
	}

	private String organisationId(MvcResult result) throws Exception {
		return body(result).get("organisation").get("id").asString();
	}

	private JsonNode body(MvcResult result) throws Exception {
		return this.json.readTree(result.getResponse().getContentAsString());
	}

}
