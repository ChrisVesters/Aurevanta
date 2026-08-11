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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
		create(identityToken(), "Nowhere Consulting", "nowhere-consulting").andExpect(status().isCreated())
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
		create(identityToken(), "Nowhere Consulting", "nowhere-consulting").andExpect(status().isCreated());

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

		create(this.accessTokens.issue(existing).value(), "Nowhere Consulting", "nowhere-consulting")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.account.organisation.slug").value("nowhere-consulting"))
			.andExpect(jsonPath("$.account.role").value("OWNER"));

		// The one they already had is untouched, with the role they hold there.
		assertThat(this.memberships.findAllForUser(this.mallory.getId())).hasSize(2)
			.extracting((membership) -> membership.getTenant().getSlug(), Membership::getRole)
			.containsExactlyInAnyOrder(Tuple.tuple("acme-planning-co", UserRole.MEMBER),
					Tuple.tuple("nowhere-consulting", UserRole.OWNER));
	}

	/** The whole point of M1a: the name is not the thing that has to be unique. */
	@Test
	void acceptsANameSomebodyElseAlreadyUses() throws Exception {
		this.tenants.save(new Tenant("Nowhere Consulting", "nowhere-consulting", CREATED_AT));

		create(identityToken(), "Nowhere Consulting", "nowhere-consulting-2").andExpect(status().isCreated())
			.andExpect(jsonPath("$.account.organisation.name").value("Nowhere Consulting"))
			.andExpect(jsonPath("$.account.organisation.slug").value("nowhere-consulting-2"));
	}

	/**
	 * The refusal M0 got wrong, aimed at the right thing — and carrying a way past
	 * itself, which is what this API offers instead of an endpoint for asking what is
	 * free.
	 */
	@Test
	void refusesAHandleSomebodyElseHasAndOffersOneTheyDoNot() throws Exception {
		this.tenants.save(new Tenant("Nowhere Consulting", "nowhere-consulting", CREATED_AT));

		create(identityToken(), "Nowhere Consulting", "nowhere-consulting").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("slug_taken"))
			.andExpect(jsonPath("$.suggested").value("nowhere-consulting-2"));

		assertThat(this.tenants.findAll()).hasSize(1);
	}

	/** Somebody already counting is offered the next number, not a number of numbers. */
	@Test
	void countsOnFromAHandleThatIsAlreadyCounting() throws Exception {
		this.tenants.save(new Tenant("Nowhere", "nowhere", CREATED_AT));
		this.tenants.save(new Tenant("Nowhere", "nowhere-2", CREATED_AT));

		create(identityToken(), "Nowhere", "nowhere-2").andExpect(status().isConflict())
			.andExpect(jsonPath("$.suggested").value("nowhere-3"));
	}

	/** Nothing is derived from the name any more, so it is simply a name. */
	@Test
	void acceptsANameNoHandleCouldBeBuiltFrom() throws Exception {
		create(identityToken(), "!!! ???", "nowhere-consulting").andExpect(status().isCreated())
			.andExpect(jsonPath("$.account.organisation.name").value("!!! ???"))
			.andExpect(jsonPath("$.account.organisation.slug").value("nowhere-consulting"));
	}

	@Test
	void rejectsAHandleThatIsNotTheShapeAHandleMustBe() throws Exception {
		create(identityToken(), "Nowhere Consulting", "Nowhere Consulting").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.slug.code").value("pattern"));

		assertThat(this.tenants.findAll()).isEmpty();
	}

	@Test
	void rejectsARequestThatNamesNoHandle() throws Exception {
		this.mvc
			.perform(post("/api/organisations").header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Nowhere Consulting\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.slug.code").value("not_blank"));
	}

	/**
	 * Stripped before validation, so a name of nothing but spaces is a name of nothing.
	 */
	@Test
	void rejectsARequestThatNamesNothing() throws Exception {
		create(identityToken(), "   ", "nowhere-consulting").andExpect(status().isBadRequest())
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
				.content(this.json
					.writeValueAsString(new CreateOrganisationRequest("Nowhere Consulting", "nowhere-consulting"))))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * A token outlives the account it names by no more than the request presenting it.
	 */
	@Test
	void refusesATokenWhoseAccountIsGone() throws Exception {
		String stale = identityToken();
		this.users.delete(this.mallory);

		create(stale, "Nowhere Consulting", "nowhere-consulting").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("invalid_credentials"));
	}

	// Changing one afterwards ------------------------------------------------

	@Test
	void anOwnerCanRenameTheirOrganisation() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");

		update(owner, "Nowhere Ltd", "nowhere-consulting").andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Nowhere Ltd"))
			// Nothing is derived from the name, so the handle stays exactly where it was.
			.andExpect(jsonPath("$.slug").value("nowhere-consulting"));
	}

	@Test
	void anOwnerCanMoveTheirOrganisationsHandle() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");

		update(owner, "Nowhere Consulting", "nowhere").andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("nowhere"));

		assertThat(this.tenants.findById(owner.getTenant().getId()).orElseThrow().getSlug()).isEqualTo("nowhere");
	}

	/**
	 * Keeping your own handle is not taking somebody's: refusing it would make renaming
	 * an organisation impossible without also moving its address.
	 */
	@Test
	void keepingTheHandleItAlreadyHasIsNotACollision() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");

		update(owner, "Nowhere Ltd", "nowhere-consulting").andExpect(status().isOk());
	}

	@Test
	void refusesAHandleSomebodyElseHasAndOffersOneTheyDoNotWhenChangingIt() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");
		this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));

		update(owner, "Nowhere Consulting", "umbrella").andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("slug_taken"))
			.andExpect(jsonPath("$.suggested").value("umbrella-2"));

		assertThat(this.tenants.findById(owner.getTenant().getId()).orElseThrow().getSlug())
			.isEqualTo("nowhere-consulting");
	}

	@Test
	void aMemberCannotChangeEither() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");
		Membership bob = this.memberships
			.save(new Membership(user("bob@nowhere.test", "Bob"), owner.getTenant(), UserRole.MEMBER, CREATED_AT));

		update(bob, "Bob's Place", "bobs-place").andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_an_owner"));

		assertThat(this.tenants.findById(owner.getTenant().getId()).orElseThrow().getName())
			.isEqualTo("Nowhere Consulting");
	}

	/**
	 * The organisation is never named in the request, so this is where renaming would
	 * reach into another one if it were. Grace owns hers and nothing else.
	 */
	@Test
	void changesOnlyTheOrganisationTheCallersTokenNames() throws Exception {
		Membership grace = owner("Umbrella", "umbrella");
		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));

		update(grace, "Umbrella Ltd", "umbrella-ltd").andExpect(status().isOk());

		assertThat(this.tenants.findById(acme.getId()).orElseThrow().getName()).isEqualTo("Acme Planning Co");
	}

	@Test
	void rejectsAHandleThatIsNotTheShapeAHandleMustBeWhenChangingIt() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");

		update(owner, "Nowhere Consulting", "Nowhere Ltd").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.slug.code").value("pattern"));
	}

	/**
	 * Both are required: the parts of a resource this API lets you change are the parts
	 * you send, so a body carrying one of them is a body missing the other.
	 */
	@Test
	void rejectsAChangeThatNamesOnlyOneOfThem() throws Exception {
		Membership owner = owner("Nowhere Consulting", "nowhere-consulting");

		this.mvc
			.perform(patch("/api/organisations")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.accessTokens.issue(owner).value())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Nowhere Ltd\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.slug.code").value("not_blank"));
	}

	@Test
	void changingRequiresATokenScopedToAnOrganisation() throws Exception {
		this.mvc
			.perform(patch("/api/organisations").header(HttpHeaders.AUTHORIZATION, "Bearer " + identityToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new UpdateOrganisationRequest("Nowhere", "nowhere"))))
			.andExpect(status().isForbidden());
	}

	/** Somebody owning an organisation, for the cases about changing one. */
	private Membership owner(String name, String slug) {
		Tenant tenant = this.tenants.save(new Tenant(name, slug, CREATED_AT));
		return this.memberships.save(new Membership(this.mallory, tenant, UserRole.OWNER, CREATED_AT));
	}

	private ResultActions update(Membership caller, String name, String slug) throws Exception {
		return this.mvc.perform(patch("/api/organisations")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.accessTokens.issue(caller).value())
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new UpdateOrganisationRequest(name, slug))));
	}

	private User user(String email, String displayName) {
		User user = new User(email, this.passwordEncoder.encode("correct-horse-battery"), displayName, CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		return this.users.save(user);
	}

	private String identityToken() {
		return this.accessTokens.issueIdentityToken(this.mallory).value();
	}

	/**
	 * Name and handle are passed separately because they are no longer derived from one
	 * another — which is the whole of what this milestone changed.
	 */
	private ResultActions create(String bearer, String name, String slug) throws Exception {
		return this.mvc.perform(post("/api/organisations").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new CreateOrganisationRequest(name, slug))));
	}

}
