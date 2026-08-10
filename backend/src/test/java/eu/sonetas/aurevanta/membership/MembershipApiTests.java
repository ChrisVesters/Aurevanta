package eu.sonetas.aurevanta.membership;

import java.time.Instant;

import eu.sonetas.aurevanta.TestcontainersConfiguration;
import eu.sonetas.aurevanta.auth.signin.LoginRequest;
import eu.sonetas.aurevanta.tenant.Tenant;
import eu.sonetas.aurevanta.tenant.TenantRepository;
import eu.sonetas.aurevanta.user.User;
import eu.sonetas.aurevanta.user.UserRepository;
import eu.sonetas.aurevanta.user.UserRole;
import org.assertj.core.groups.Tuple;
import eu.sonetas.aurevanta.ratelimit.SignInRateLimiter;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The multi-organisation half of authentication, which registration alone cannot reach:
 * an identity may hold several memberships, or none.
 *
 * <p>
 * The fixture deliberately holds <em>two</em> organisations, because the hazard in this
 * design is the exchange endpoint. It takes an organisation identifier from the request,
 * so a test with a single tenant would pass whether or not membership is really checked.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MembershipApiTests {

	private static final String PASSWORD = "correct-horse-battery";

	private static final Instant CREATED_AT = Instant.parse("2026-08-06T08:00:00Z");

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
	private PasswordEncoder passwordEncoder;

	private Tenant acme;

	private Tenant umbrella;

	private User ada;

	private User grace;

	private User mallory;

	/**
	 * Shared across every case here, and failed sign-ins accumulate against one source.
	 */
	@Autowired
	private SignInRateLimiter signInRateLimiter;

	@BeforeEach
	void seedTwoOrganisations() {
		this.signInRateLimiter.clear();
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();

		this.acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		this.umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = user("ada@acme.test", "Ada");
		this.grace = user("grace@umbrella.test", "Grace");
		// Somebody who belongs to nothing: the state reached by being removed from your
		// only organisation.
		this.mallory = user("mallory@nowhere.test", "Mallory");

		// Ada owns one organisation and is merely a member of the other — the whole point
		// of moving the role off the account.
		this.memberships.save(new Membership(this.ada, this.acme, UserRole.OWNER, CREATED_AT));
		this.memberships.save(new Membership(this.ada, this.umbrella, UserRole.MEMBER, CREATED_AT));
		this.memberships.save(new Membership(this.grace, this.umbrella, UserRole.OWNER, CREATED_AT));
	}

	@Test
	void oneAddressHoldsADifferentRoleInEachOrganisation() {
		assertThat(this.memberships.findAllForUser(this.ada.getId()))
			.extracting((membership) -> membership.getTenant().getSlug(), Membership::getRole)
			.containsExactlyInAnyOrder(Tuple.tuple("acme-planning-co", UserRole.OWNER),
					Tuple.tuple("umbrella", UserRole.MEMBER));
	}

	@Test
	void signingInWithSeveralMembershipsOffersTheChoiceInsteadOfPickingOne() throws Exception {
		this.mvc.perform(login("ada@acme.test"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("CHOOSE_ORGANISATION"))
			.andExpect(jsonPath("$.identity.identityToken").isNotEmpty())
			.andExpect(jsonPath("$.identity.memberships.length()").value(2))
			// No session at all: an organisation has not been chosen, so there is nothing
			// to act as yet.
			.andExpect(jsonPath("$.session").doesNotExist());
	}

	@Test
	void signingInWithNoMembershipsStillIdentifiesTheCaller() throws Exception {
		this.mvc.perform(login("mallory@nowhere.test"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.outcome").value("NO_ORGANISATION"))
			.andExpect(jsonPath("$.identity.identityToken").isNotEmpty())
			.andExpect(jsonPath("$.identity.memberships").isEmpty())
			.andExpect(jsonPath("$.session").doesNotExist());
	}

	@Test
	void exchangingAnIdentityTokenGivesASessionScopedToTheChosenOrganisation() throws Exception {
		this.mvc.perform(exchange(this.umbrella, identityTokenFor("ada@acme.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.account.email").value("ada@acme.test"))
			.andExpect(jsonPath("$.account.organisation.slug").value("umbrella"))
			// The role that comes back is the one held here, not the one held elsewhere.
			.andExpect(jsonPath("$.account.role").value("MEMBER"));
	}

	@Test
	void exchangingForTheOtherOrganisationGivesTheRoleHeldThere() throws Exception {
		this.mvc.perform(exchange(this.acme, identityTokenFor("ada@acme.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.organisation.slug").value("acme-planning-co"))
			.andExpect(jsonPath("$.account.role").value("OWNER"));
	}

	/**
	 * The one test to review hardest in this step. The endpoint takes an organisation
	 * from the path, so were it looked up by tenant alone rather than by membership, any
	 * authenticated caller could mint a token for any organisation in the installation.
	 */
	@Test
	void exchangingForAnOrganisationTheCallerDoesNotBelongToIsRefused() throws Exception {
		this.mvc.perform(exchange(this.acme, identityTokenFor("mallory@nowhere.test")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	/** The same refusal for a caller who does hold a session, just not to that tenant. */
	@Test
	void anAccessTokenCannotBeExchangedForAnOrganisationItsHolderIsNotIn() throws Exception {
		this.mvc.perform(exchange(this.acme, accessTokenFor("grace@umbrella.test")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	/** Switching organisations: an access token exchanges for another of its holder's. */
	@Test
	void anAccessTokenCanBeExchangedForAnotherOrganisationItsHolderBelongsTo() throws Exception {
		String adaInAcme = tokenFrom(exchange(this.acme, identityTokenFor("ada@acme.test")));

		this.mvc.perform(exchange(this.umbrella, adaInAcme))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.account.organisation.slug").value("umbrella"))
			.andExpect(jsonPath("$.account.role").value("MEMBER"));
	}

	@Test
	void exchangingRequiresAToken() throws Exception {
		this.mvc.perform(post("/api/auth/tenants/" + this.acme.getId() + "/token"))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * An identity token names a person and no organisation, so it must reach nothing that
	 * serves tenant-owned data — however valid its signature.
	 */
	@Test
	void anIdentityTokenIsRefusedOnATenantScopedEndpoint() throws Exception {
		this.mvc
			.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION,
					"Bearer " + identityTokenFor("ada@acme.test")))
			.andExpect(status().isForbidden());
	}

	/**
	 * Grace belongs to Umbrella, and so does Ada. A list built from the tenant rather
	 * than from the caller would show both memberships in it.
	 */
	@Test
	void theMembershipListShowsOnlyTheCallersOwnOrganisations() throws Exception {
		this.mvc
			.perform(get("/api/memberships").header(HttpHeaders.AUTHORIZATION,
					"Bearer " + accessTokenFor("grace@umbrella.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].organisation.slug").value("umbrella"))
			.andExpect(jsonPath("$[0].role").value("OWNER"));
	}

	@Test
	void theMembershipListIsReachableWithAnIdentityTokenToo() throws Exception {
		this.mvc
			.perform(get("/api/memberships").header(HttpHeaders.AUTHORIZATION,
					"Bearer " + identityTokenFor("ada@acme.test")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void theMembershipListRequiresAToken() throws Exception {
		this.mvc.perform(get("/api/memberships")).andExpect(status().isUnauthorized());
	}

	/** So a later sign-in can offer the organisation the person was last working in. */
	@Test
	void choosingAnOrganisationPutsItAtTheHeadOfTheList() throws Exception {
		String identity = identityTokenFor("ada@acme.test");
		this.mvc.perform(exchange(this.umbrella, identity)).andExpect(status().isOk());

		this.mvc.perform(get("/api/memberships").header(HttpHeaders.AUTHORIZATION, "Bearer " + identity))
			.andExpect(jsonPath("$[0].organisation.slug").value("umbrella"))
			.andExpect(jsonPath("$[0].lastAccessedAt").isNotEmpty())
			.andExpect(jsonPath("$[1].organisation.slug").value("acme-planning-co"))
			.andExpect(jsonPath("$[1].lastAccessedAt").doesNotExist());
	}

	/**
	 * Signing into the only organisation you hold counts as choosing it, so the ordering
	 * is already right the moment a second membership appears.
	 */
	@Test
	void signingStraightIntoTheOnlyOrganisationRecordsTheChoiceToo() throws Exception {
		this.mvc.perform(login("grace@umbrella.test")).andExpect(jsonPath("$.outcome").value("SIGNED_IN"));

		assertThat(this.memberships.findAllForUser(this.grace.getId()).getFirst().getLastAccessedAt()).isNotNull();
	}

	/** The count Step 11 leans on to stop an organisation being left with no owner. */
	@Test
	void ownersAreCountedPerOrganisation() {
		assertThat(this.memberships.countByTenantIdAndRole(this.umbrella.getId(), UserRole.OWNER)).isEqualTo(1);
		assertThat(this.memberships.countByTenantIdAndRole(this.umbrella.getId(), UserRole.MEMBER)).isEqualTo(1);
		assertThat(this.memberships.countByTenantIdAndRole(this.acme.getId(), UserRole.MEMBER)).isZero();
	}

	@Test
	void findsNoMembershipForAnOrganisationTheCallerIsNotIn() {
		assertThat(this.memberships.findForUserInTenant(this.mallory.getId(), this.acme.getId())).isEmpty();
	}

	/**
	 * Seeded already confirmed. These fixtures exist to exercise membership, and an
	 * unconfirmed address is refused at sign-in — which is its own test, over in
	 * {@code AuthApiTests}, not a precondition every test here should have to restate.
	 */
	private User user(String email, String displayName) {
		User user = new User(email, this.passwordEncoder.encode(PASSWORD), displayName, CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		return this.users.save(user);
	}

	private RequestBuilder login(String email) {
		return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new LoginRequest(email, PASSWORD)));
	}

	private RequestBuilder exchange(Tenant tenant, String token) {
		return post("/api/auth/tenants/" + tenant.getId() + "/token").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + token);
	}

	/** Only meaningful for someone holding no membership, or more than one. */
	private String identityTokenFor(String email) throws Exception {
		return signIn(email).get("identity").get("identityToken").asString();
	}

	/** Only meaningful for someone holding exactly one membership. */
	private String accessTokenFor(String email) throws Exception {
		return signIn(email).get("session").get("accessToken").asString();
	}

	private JsonNode signIn(String email) throws Exception {
		return body(this.mvc.perform(login(email)).andExpect(status().isOk()).andReturn());
	}

	private String tokenFrom(RequestBuilder request) throws Exception {
		return body(this.mvc.perform(request).andExpect(status().isOk()).andReturn()).get("accessToken").asString();
	}

	private JsonNode body(MvcResult result) throws Exception {
		return this.json.readTree(result.getResponse().getContentAsString());
	}

}
