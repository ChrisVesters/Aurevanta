package com.cvesters.aurevanta.tenant;

import java.time.Instant;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starting an organisation from an account that already exists.
 *
 * <p>
 * The way out of belonging to nothing, which became a reachable state the moment an owner
 * could remove people. Without it the only route back would be waiting for somebody else
 * to invite you, and waiting is not something a person can do for themselves.
 *
 * <p>
 * Which is why an <em>identity</em> token has to reach this: the caller who needs it most
 * has no organisation, and therefore no access token to offer.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OrganisationApiTests {

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

	@Autowired
	private AccessTokenService accessTokens;

	/** Belongs to nothing, exactly as somebody removed from their only organisation. */
	private User mallory;

	@BeforeEach
	void seedAnAccountWithNothingInIt() {
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();

		this.mallory = user("mallory@nowhere.test", "Mallory");
	}

	@Test
	void somebodyWhoBelongsToNothingCanStartAnOrganisation() throws Exception {
		create(identityToken(), "Nowhere Consulting").andExpect(status().isCreated())
			// A session for it straight away, so the caller is working in the thing they
			// just made rather than having to trade a token for it.
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andExpect(jsonPath("$.account.email").value("mallory@nowhere.test"))
			.andExpect(jsonPath("$.account.organisation.name").value("Nowhere Consulting"))
			.andExpect(jsonPath("$.account.organisation.slug").value("nowhere-consulting"))
			// Whoever starts one administers it; there is nobody else to.
			.andExpect(jsonPath("$.account.role").value("OWNER"));

		assertThat(this.memberships.findAllForUser(this.mallory.getId())).singleElement()
			.satisfies((membership) -> assertThat(membership.getRole()).isEqualTo(UserRole.OWNER));
	}

	/** So a later sign-in offers the one they were most recently working in. */
	@Test
	void startingOneCountsAsChoosingIt() throws Exception {
		create(identityToken(), "Nowhere Consulting").andExpect(status().isCreated());

		assertThat(this.memberships.findAllForUser(this.mallory.getId()).getFirst().getLastAccessedAt()).isNotNull();
	}

	/**
	 * Somebody already in one organisation starting a second — the same endpoint, and the
	 * ordinary case once identity is separate from membership.
	 */
	@Test
	void somebodyWhoAlreadyBelongsSomewhereCanStartAnother() throws Exception {
		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Membership existing = this.memberships.save(new Membership(this.mallory, acme, UserRole.MEMBER, CREATED_AT));

		create(this.accessTokens.issue(existing).value(), "Nowhere Consulting").andExpect(status().isCreated())
			.andExpect(jsonPath("$.account.organisation.slug").value("nowhere-consulting"))
			.andExpect(jsonPath("$.account.role").value("OWNER"));

		// The one they already had is untouched, with the role they hold there.
		assertThat(this.memberships.findAllForUser(this.mallory.getId())).hasSize(2)
			.extracting((membership) -> membership.getTenant().getSlug(), Membership::getRole)
			.containsExactlyInAnyOrder(Tuple.tuple("acme-planning-co", UserRole.MEMBER),
					Tuple.tuple("nowhere-consulting", UserRole.OWNER));
	}

	/** The same rules registration applies, because it is the same code applying them. */
	@Test
	void refusesANameAlreadyTaken() throws Exception {
		this.tenants.save(new Tenant("Nowhere Consulting", "nowhere-consulting", CREATED_AT));

		create(identityToken(), "Nowhere  Consulting!").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("organisation_name_unavailable"));
	}

	@Test
	void refusesANameNoHandleCanBeBuiltFrom() throws Exception {
		create(identityToken(), "!!! ???").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("organisation_name_unusable"));

		assertThat(this.tenants.findAll()).isEmpty();
	}

	/**
	 * Stripped before validation, so a name of nothing but spaces is a name of nothing.
	 */
	@Test
	void rejectsARequestThatNamesNothing() throws Exception {
		create(identityToken(), "   ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));
	}

	/**
	 * A body with no name at all, which is a different shape from one with a blank name.
	 */
	@Test
	void rejectsARequestWithNoNameAtAll() throws Exception {
		this.mvc
			.perform(post("/api/organisations").header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc
			.perform(post("/api/organisations").contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new CreateOrganisationRequest("Nowhere Consulting"))))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * A token outlives the account it names by no more than the request presenting it.
	 */
	@Test
	void refusesATokenWhoseAccountIsGone() throws Exception {
		String stale = identityToken();
		this.users.delete(this.mallory);

		create(stale, "Nowhere Consulting").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	private User user(String email, String displayName) {
		User user = new User(email, this.passwordEncoder.encode("correct-horse-battery"), displayName, CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		return this.users.save(user);
	}

	private String identityToken() {
		return this.accessTokens.issueIdentityToken(this.mallory).value();
	}

	private ResultActions create(String bearer, String name) throws Exception {
		return this.mvc.perform(post("/api/organisations").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new CreateOrganisationRequest(name))));
	}

}
