package com.cvesters.aurevanta.resource;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The pools an organisation says it has.
 *
 * <p>
 * <strong>The case worth reading first is {@link #twoPoolsMayShareOneName}.</strong> M1a
 * spent a whole milestone undoing a uniqueness constraint nobody chose, and every table
 * since has had to decide deliberately whether a name addresses anything. This one does
 * not: two teams called "Designers" is somebody's business.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ResourceApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-10T08:00:00Z");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private UserRepository users;

	@Autowired
	private MembershipRepository memberships;

	@Autowired
	private TenantRepository tenants;

	@Autowired
	private ResourceRepository resources;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership bob;

	private Membership grace;

	@BeforeEach
	void seedTwoOrganisations() {
		this.resources.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.bob = member(user("bob@acme.test", "Bob"), acme, UserRole.MEMBER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
	}

	// Declaring one ------------------------------------------------------------

	@Test
	void declaresAPoolAndReadsItBack() throws Exception {
		declare("{\"name\":\"Backend engineers\",\"units\":3}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Backend engineers"))
			.andExpect(jsonPath("$.units").value(3))
			.andExpect(jsonPath("$.personId").value(nullValue()))
			.andExpect(jsonPath("$.archivedAt").value(nullValue()));

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Backend engineers"));
	}

	/**
	 * <strong>A pool of one with a person on it, which is the whole of what "people are
	 * one type" needs.</strong> No second concept, no hierarchy: the roadmap's people,
	 * environments and licences are three rows of one table.
	 */
	@Test
	void aPersonIsAPoolOfOne() throws Exception {
		String body = "{\"name\":\"Ada\",\"units\":1,\"personId\":\"" + this.ada.getUser().getId() + "\"}";

		declare(body).andExpect(status().isCreated())
			.andExpect(jsonPath("$.units").value(1))
			.andExpect(jsonPath("$.personId").value(this.ada.getUser().getId().toString()))
			// The name comes with it, so a screen drawing a team does not have to ask who
			// everybody is a second time.
			.andExpect(jsonPath("$.personName").value("Ada"));
	}

	/**
	 * Somebody in another organisation is refused, and the refusal is its own code: one
	 * saying <em>you</em> may not be here and one saying <em>they</em> are not are
	 * different things, and a single code for both would have somebody re-authenticating
	 * over a mistyped colleague.
	 */
	@Test
	void aPoolCannotBeNamedAfterAStranger() throws Exception {
		String body = "{\"name\":\"Grace\",\"units\":1,\"personId\":\"" + this.grace.getUser().getId() + "\"}";

		declare(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("person_not_a_member"));
	}

	@Test
	void anAccountNobodyHasIsTheSameAnswer() throws Exception {
		String body = "{\"name\":\"Nobody\",\"units\":1,\"personId\":\"" + UUID.randomUUID() + "\"}";

		declare(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("person_not_a_member"));
	}

	// What it refuses ----------------------------------------------------------

	/**
	 * <strong>A pool of nothing schedules nothing.</strong> Required and positive, with
	 * no default anywhere — which is the argument {@code capacity} has always made,
	 * arriving on the thing that replaces it.
	 */
	@Test
	void aPoolOfNothingIsRefused() throws Exception {
		refused("{\"name\":\"Nobody at all\",\"units\":0}", "units", "positive");
		refused("{\"name\":\"Fewer than nobody\",\"units\":-2}", "units", "positive");
		refused("{\"name\":\"Unsaid\"}", "units", "not_null");
	}

	/**
	 * Empty, all spaces, and never sent at all are one answer: there has to be a name.
	 */
	@Test
	void aPoolNeedsAName() throws Exception {
		refused("{\"name\":\"\",\"units\":2}", "name", "not_blank");
		refused("{\"name\":\"   \",\"units\":2}", "name", "not_blank");
		refused("{\"units\":2}", "name", "not_blank");
	}

	/** And the same on the way through, where a name that is not sent is not a rename. */
	@Test
	void aPoolStillNeedsANameWhenItChanges() throws Exception {
		UUID pool = declared("{\"name\":\"Backend engineers\",\"units\":3}");

		this.mvc
			.perform(patch("/api/resources/" + pool).header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"units\":4}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));
	}

	/** A bound on absurdity rather than on ambition — a mistyped year is not a team. */
	@Test
	void aPoolLargerThanAnyTeamIsRefused() throws Exception {
		refused("{\"name\":\"Everyone\",\"units\":100000}", "units", "max");
	}

	/**
	 * M1a's lesson, applied before the mistake rather than after it: nothing is derived
	 * from a name and nothing routes by one, so two pools may share one.
	 */
	@Test
	void twoPoolsMayShareOneName() throws Exception {
		declare("{\"name\":\"Designers\",\"units\":2}").andExpect(status().isCreated());
		declare("{\"name\":\"Designers\",\"units\":1}").andExpect(status().isCreated());

		read(this.ada).andExpect(jsonPath("$.length()").value(2));
	}

	// Changing and putting away -------------------------------------------------

	@Test
	void changesWhatAPoolIsAndWhoItIs() throws Exception {
		UUID pool = declared("{\"name\":\"Backend engineers\",\"units\":3}");
		String body = "{\"name\":\"Platform engineers\",\"units\":4,\"personId\":\"" + this.bob.getUser().getId()
				+ "\"}";

		this.mvc
			.perform(patch("/api/resources/" + pool).header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Platform engineers"))
			.andExpect(jsonPath("$.units").value(4))
			.andExpect(jsonPath("$.personName").value("Bob"));
	}

	/** A null person clears the link, which is the one field here that can be cleared. */
	@Test
	void aPoolCanStopBeingAParticularPerson() throws Exception {
		UUID pool = declared("{\"name\":\"Ada\",\"units\":1,\"personId\":\"" + this.ada.getUser().getId() + "\"}");

		this.mvc
			.perform(patch("/api/resources/" + pool).header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"A designer\",\"units\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.personId").value(nullValue()))
			.andExpect(jsonPath("$.personName").value(nullValue()));
	}

	/**
	 * <strong>Put away and not deleted</strong>, like every other domain row — and here
	 * for a reason of its own: a forecast stores the declaration it was scheduled under,
	 * so a pool that had vanished would leave that snapshot describing an identifier.
	 */
	@Test
	void aPoolIsPutAwayRatherThanDestroyed() throws Exception {
		UUID pool = declared("{\"name\":\"Staging environment\",\"units\":1}");

		this.mvc
			.perform(post("/api/resources/" + pool + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").isNotEmpty());

		read(this.ada).andExpect(jsonPath("$.length()").value(0));
		this.mvc
			.perform(
					get("/api/resources").param("archived", "true").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(pool.toString()));
	}

	/** Archiving twice keeps the first moment, the way archiving a plan does. */
	@Test
	void puttingAPoolAwayTwiceKeepsTheFirstMoment() throws Exception {
		UUID pool = declared("{\"name\":\"Staging environment\",\"units\":1}");
		String first = archived(pool);

		this.mvc
			.perform(post("/api/resources/" + pool + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.archivedAt").value(first));
	}

	@Test
	void aPoolComesBack() throws Exception {
		UUID pool = declared("{\"name\":\"Staging environment\",\"units\":1}");
		archived(pool);

		this.mvc
			.perform(post("/api/resources/" + pool + "/unarchive").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").value(nullValue()));
		read(this.ada).andExpect(jsonPath("$.length()").value(1));
	}

	// Who may see it ------------------------------------------------------------

	/** Any member, because saying what a team is made of is planning. */
	@Test
	void anyMemberMayDeclareAndChangeThem() throws Exception {
		UUID pool = declared("{\"name\":\"Backend engineers\",\"units\":3}");

		this.mvc
			.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(this.bob))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Frontend engineers\",\"units\":2}"))
			.andExpect(status().isCreated());
		this.mvc
			.perform(post("/api/resources/" + pool + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(this.bob)))
			.andExpect(status().isOk());
	}

	@Test
	void anotherOrganisationsPoolIsNotThere() throws Exception {
		UUID pool = declared("{\"name\":\"Backend engineers\",\"units\":3}");

		this.mvc
			.perform(patch("/api/resources/" + pool).header(HttpHeaders.AUTHORIZATION, bearer(this.grace))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Ours now\",\"units\":9}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("resource_not_found"));
		read(this.grace).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void anIdentifierNothingHasIsTheSameAnswer() throws Exception {
		this.mvc
			.perform(post("/api/resources/" + UUID.randomUUID() + "/archive").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("resource_not_found"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/resources")).andExpect(status().isUnauthorized());
	}

	@Test
	void requiresATokenScopedToAnOrganisation() throws Exception {
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, "Bearer " + identity))
			.andExpect(status().isForbidden());
	}

	// Fixtures ------------------------------------------------------------------

	private ResultActions declare(String body) throws Exception {
		return this.mvc.perform(post("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private UUID declared(String body) throws Exception {
		String response = declare(body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(response.split("\"id\":\"")[1].split("\"")[0]);
	}

	private String archived(UUID pool) throws Exception {
		return this.mvc
			.perform(post("/api/resources/" + pool + "/archive").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString()
			.split("\"archivedAt\":\"")[1].split("\"")[0];
	}

	private ResultActions read(Membership caller) throws Exception {
		return this.mvc.perform(get("/api/resources").header(HttpHeaders.AUTHORIZATION, bearer(caller)))
			.andExpect(status().isOk());
	}

	private void refused(String body, String field, String code) throws Exception {
		declare(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field + ".code").value(code));
	}

	private String bearer(Membership caller) {
		return "Bearer " + this.accessTokens.issue(caller).value();
	}

	private Membership member(User user, Tenant tenant, UserRole role) {
		return this.memberships.save(new Membership(user, tenant, role, CREATED_AT));
	}

	private User user(String email, String displayName) {
		User user = new User(email, this.passwordEncoder.encode("correct-horse-battery"), displayName, CREATED_AT);
		user.markEmailVerified(CREATED_AT);
		return this.users.save(user);
	}

}
