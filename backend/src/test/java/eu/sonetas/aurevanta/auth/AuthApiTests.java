package eu.sonetas.aurevanta.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import eu.sonetas.aurevanta.TestcontainersConfiguration;
import eu.sonetas.aurevanta.auth.registration.RegistrationRequest;
import eu.sonetas.aurevanta.auth.signin.LoginRequest;
import eu.sonetas.aurevanta.membership.MembershipRepository;
import eu.sonetas.aurevanta.tenant.TenantRepository;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRepository;
import eu.sonetas.aurevanta.user.UserRole;
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

@Import(TestcontainersConfiguration.class)
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

	@BeforeEach
	void clearAccounts() {
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();
	}

	@Test
	void registrationCreatesAnOrganisationOwnedByTheRegisteringUser() throws Exception {
		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Acme Planning Co", "ada@acme.test")))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.expiresInSeconds").value(12 * 60 * 60))
			.andExpect(jsonPath("$.account.email").value("ada@acme.test"))
			.andExpect(jsonPath("$.account.displayName").value("Ada"))
			.andExpect(jsonPath("$.account.role").value("OWNER"))
			.andExpect(jsonPath("$.account.organisation.name").value("Acme Planning Co"))
			.andExpect(jsonPath("$.account.organisation.slug").value("acme-planning-co"))
			.andExpect(jsonPath("$.account.organisation.id").isNotEmpty());
	}

	@Test
	void registrationStoresThePasswordOnlyAsAHash() throws Exception {
		register("Acme", "ada@acme.test");

		User stored = this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow();

		assertThat(stored.getPasswordHash()).doesNotContain(PASSWORD).startsWith("{bcrypt}$2");
	}

	/** The account is the identity; the organisation and role hang off a membership. */
	@Test
	void registrationRecordsTheOwnerAsAMemberOfTheOrganisationItCreated() throws Exception {
		register("Acme", "ada@acme.test");

		User stored = this.users.findByEmailIgnoringCase("ada@acme.test").orElseThrow();

		assertThat(this.memberships.findAllForUser(stored.getId())).singleElement().satisfies((membership) -> {
			assertThat(membership.getRole()).isEqualTo(UserRole.OWNER);
			assertThat(membership.getTenant().getSlug()).isEqualTo("acme");
		});
	}

	@Test
	void eachRegistrationGetsItsOwnTenant() throws Exception {
		String first = organisationId(register("Acme", "ada@acme.test"));
		String second = organisationId(register("Umbrella", "grace@umbrella.test"));

		assertThat(first).isNotEqualTo(second);
	}

	@Test
	void registrationRejectsAnEmailThatDiffersOnlyByCase() throws Exception {
		register("Acme", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("Umbrella", "ADA@Acme.test")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("email_already_registered"));
	}

	@Test
	void registrationRejectsAnOrganisationNameAlreadyInUse() throws Exception {
		register("Acme Planning Co", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("acme planning co.", "grace@acme.test")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("organisation_name_unavailable"));
	}

	@Test
	void registrationRejectsAnOrganisationNameWithNothingToBuildAHandleFrom() throws Exception {
		this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration("!!! ???", "ada@acme.test")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("organisation_name_unusable"));
	}

	@Test
	void registrationRejectsAShortPasswordAndSaysWhichFieldFailed() throws Exception {
		String body = this.json.writeValueAsString(new RegistrationRequest("Acme", "Ada", "ada@acme.test", "short"));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.password").isNotEmpty());
	}

	@Test
	void registrationRejectsAMalformedEmail() throws Exception {
		String body = this.json.writeValueAsString(new RegistrationRequest("Acme", "Ada", "not-an-address", PASSWORD));

		this.mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.email").isNotEmpty());
	}

	/**
	 * One membership is the only state registration can produce, so the common path signs
	 * the caller straight in with nothing to choose between.
	 */
	@Test
	void loginSignsStraightInWhenTheAccountHoldsOneMembership() throws Exception {
		register("Acme", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ADA@ACME.TEST", PASSWORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("SIGNED_IN"))
			.andExpect(jsonPath("$.session.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.session.account.organisation.slug").value("acme"))
			.andExpect(jsonPath("$.identity").doesNotExist());
	}

	@Test
	void loginRejectsAWrongPassword() throws Exception {
		register("Acme", "ada@acme.test");

		this.mvc
			.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(login("ada@acme.test", "not-the-password")))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
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
		String token = accessToken(register("Acme Planning Co", "ada@acme.test"));

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
		String token = accessToken(register("Acme", "ada@acme.test"));
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
		String token = accessToken(register("Acme", "ada@acme.test"));
		this.memberships.deleteAll();

		this.mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	private MvcResult register(String organisation, String email) throws Exception {
		return this.mvc
			.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content(registration(organisation, email)))
			.andExpect(status().isCreated())
			.andReturn();
	}

	private String registration(String organisation, String email) throws Exception {
		return this.json.writeValueAsString(new RegistrationRequest(organisation, "Ada", email, PASSWORD));
	}

	private String login(String email, String password) throws Exception {
		return this.json.writeValueAsString(new LoginRequest(email, password));
	}

	private String accessToken(MvcResult result) throws Exception {
		return body(result).get("accessToken").asString();
	}

	private String organisationId(MvcResult result) throws Exception {
		return body(result).get("account").get("organisation").get("id").asString();
	}

	private JsonNode body(MvcResult result) throws Exception {
		return this.json.readTree(result.getResponse().getContentAsString());
	}

}
