package com.cvesters.aurevanta.project;

import java.time.Instant;
import java.util.UUID;

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
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Somewhere to put a plan.
 *
 * <p>
 * Two organisations throughout, because the thing most worth failing here is a leak: a
 * fixture with one tenant in it cannot tell a correctly scoped query from one that forgot
 * to scope at all. Ada owns Acme and Grace owns Umbrella, and neither can see the other's
 * work by any route this controller offers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProjectApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-13T08:00:00Z");

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
	private ProjectRepository projects;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	/** Owns Acme. */
	private Membership ada;

	/** Belongs to Acme without owning it — the case decision 6 is about. */
	private Membership bob;

	/** Owns Umbrella, and is who a leak would leak to. */
	private Membership grace;

	@BeforeEach
	void seedTwoOrganisations() {
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.bob = member(user("bob@acme.test", "Bob"), acme, UserRole.MEMBER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
	}

	// Making one --------------------------------------------------------------

	@Test
	void aMemberCanStartAPlan() throws Exception {
		create(this.bob, "Q3 platform work", "Everything we promised the board").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.name").value("Q3 platform work"))
			.andExpect(jsonPath("$.description").value("Everything we promised the board"))
			.andExpect(jsonPath("$.createdAt").isNotEmpty())
			// In use, and the absence of a moment is how a client tells that.
			.andExpect(jsonPath("$.archivedAt").value(nullValue()));

		// It belongs to the organisation the caller's token named, and to no other.
		assertThat(this.projects.findAllInTenant(this.ada.getTenant().getId(), false)).singleElement()
			.satisfies((project) -> assertThat(project.getName()).isEqualTo("Q3 platform work"),
					(project) -> assertThat(project.getTenant().getSlug()).isEqualTo("acme-planning-co"));
		assertThat(this.projects.findAllInTenant(this.grace.getTenant().getId(), false)).isEmpty();
	}

	/** A project only just named has nothing said about it yet. */
	@Test
	void aDescriptionIsOptional() throws Exception {
		create(this.ada, "Q3 platform work", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").value(nullValue()));
	}

	/**
	 * The lesson chosen handles paid for, applied before the mistake rather than after
	 * it: a team running the same shape of work every quarter is the ordinary case, and
	 * the identifier is what addresses one.
	 */
	@Test
	void twoProjectsMayShareAName() throws Exception {
		create(this.ada, "Q3 platform work", null).andExpect(status().isCreated());

		create(this.ada, "Q3 platform work", null).andExpect(status().isCreated());

		assertThat(this.projects.findAll()).hasSize(2);
	}

	@Test
	void rejectsAPlanWithNoName() throws Exception {
		create(this.ada, "   ", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));

		assertThat(this.projects.findAll()).isEmpty();
	}

	@Test
	void rejectsANameLongerThanTheColumn() throws Exception {
		create(this.ada, "Q".repeat(201), null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("max_size"))
			.andExpect(jsonPath("$.errors.name.max").value(200));
	}

	@Test
	void rejectsADescriptionLongerThanTheColumn() throws Exception {
		create(this.ada, "Q3 platform work", "x".repeat(2001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.description.code").value("max_size"));
	}

	/**
	 * Normalised before validation, so a name pasted with a trailing space is not stored
	 * with one and a description of nothing but spaces is stored as nothing.
	 *
	 * <p>
	 * The body is written by hand rather than serialised from the record, which would
	 * strip it here and leave the server with nothing to prove.
	 */
	@Test
	void stripsWhatWasTypedBeforeItIsJudged() throws Exception {
		createFromBody("{\"name\":\"  Q3 platform work  \",\"description\":\"   \"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("Q3 platform work"))
			.andExpect(jsonPath("$.description").value(nullValue()));
	}

	/** A body with no name at all, which is a different shape from a blank one. */
	@Test
	void rejectsARequestWithNoNameAtAll() throws Exception {
		createFromBody("{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));
	}

	// Reading them ------------------------------------------------------------

	@Test
	void listsThePlansOfTheCallersOwnOrganisationAndNobodyElses() throws Exception {
		project(this.ada, "Q3 platform work");
		project(this.grace, "Umbrella's secret plan");

		list(this.bob).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Q3 platform work"));
	}

	/** By name, so the list does not rearrange itself between requests. */
	@Test
	void listsThemInAnOrderThatDoesNotMove() throws Exception {
		project(this.ada, "Migration");
		project(this.ada, "API v2");
		project(this.ada, "Zero downtime");

		list(this.ada).andExpect(jsonPath("$[0].name").value("API v2"))
			.andExpect(jsonPath("$[1].name").value("Migration"))
			.andExpect(jsonPath("$[2].name").value("Zero downtime"));
	}

	@Test
	void readsOneByItsIdentifier() throws Exception {
		Project project = project(this.ada, "Q3 platform work");

		this.mvc.perform(get("/api/projects/" + project.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.bob)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Q3 platform work"));
	}

	/**
	 * The isolation rule seen from the endpoint that would break it: an identifier from
	 * another organisation answers exactly as one that never existed, so this cannot be
	 * used to discover which projects exist elsewhere.
	 */
	@Test
	void aPlanInAnotherOrganisationIsNotFound() throws Exception {
		Project theirs = project(this.grace, "Umbrella's secret plan");

		this.mvc.perform(get("/api/projects/" + theirs.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	@Test
	void anIdentifierNothingHasIsTheSameAnswer() throws Exception {
		this.mvc.perform(get("/api/projects/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	// Changing one ------------------------------------------------------------

	@Test
	void aMemberCanRenameAPlanAndSayWhatItIsFor() throws Exception {
		Project project = project(this.ada, "Q3 platform work");

		update(this.bob, project, "Q4 platform work", "Slipped a quarter").andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Q4 platform work"))
			.andExpect(jsonPath("$.description").value("Slipped a quarter"));
	}

	/** The only way to take back something somebody wrote, so it has to be reachable. */
	@Test
	void aDescriptionCanBeCleared() throws Exception {
		Project project = this.projects
			.save(new Project(this.ada.getTenant(), "Q3 platform work", "Everything we promised", CREATED_AT));

		update(this.ada, project, "Q3 platform work", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value(nullValue()));

		assertThat(this.projects.findById(project.getId()).orElseThrow().getDescription()).isNull();
	}

	/** A column that cannot be empty has no reading of a missing name that is right. */
	@Test
	void rejectsAChangeThatNamesNothing() throws Exception {
		Project project = project(this.ada, "Q3 platform work");

		update(this.ada, project, "  ", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));

		assertThat(this.projects.findById(project.getId()).orElseThrow().getName()).isEqualTo("Q3 platform work");
	}

	/**
	 * The same shape of body, against the record that takes a change rather than a start.
	 */
	@Test
	void rejectsAChangeWithNoNameAtAll() throws Exception {
		Project project = project(this.ada, "Q3 platform work");

		this.mvc
			.perform(patch("/api/projects/" + project.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.name.code").value("not_blank"));
	}

	@Test
	void cannotRenameAPlanInAnotherOrganisation() throws Exception {
		Project theirs = project(this.grace, "Umbrella's secret plan");

		update(this.ada, theirs, "Ours now", null).andExpect(status().isNotFound());

		assertThat(this.projects.findById(theirs.getId()).orElseThrow().getName()).isEqualTo("Umbrella's secret plan");
	}

	// Putting one away --------------------------------------------------------

	@Test
	void archivingKeepsThePlanOutOfTheDefaultListing() throws Exception {
		Project project = project(this.ada, "Q3 platform work");

		archive(this.bob, project, "archive").andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").isNotEmpty());

		list(this.ada).andExpect(jsonPath("$.length()").value(0));
		listArchived(this.ada).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].name").value("Q3 platform work"));
		// Nothing is destroyed: the row is what the estimates hanging off it will need.
		assertThat(this.projects.findAll()).hasSize(1);
	}

	@Test
	void unarchivingBringsItBack() throws Exception {
		Project project = project(this.ada, "Q3 platform work");
		archive(this.ada, project, "archive").andExpect(status().isOk());

		archive(this.ada, project, "unarchive").andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").value(nullValue()));

		list(this.ada).andExpect(jsonPath("$.length()").value(1));
		listArchived(this.ada).andExpect(jsonPath("$.length()").value(0));
	}

	/**
	 * Archiving something already archived is one decision arriving twice, not a new one
	 * — and the moment it was put away is what an ordering by that moment would read.
	 */
	@Test
	void archivingTwiceKeepsTheMomentItWasFirstPutAway() throws Exception {
		Project project = project(this.ada, "Q3 platform work");
		archive(this.ada, project, "archive").andExpect(status().isOk());
		Instant first = this.projects.findById(project.getId()).orElseThrow().getArchivedAt();

		archive(this.ada, project, "archive").andExpect(status().isOk());

		assertThat(this.projects.findById(project.getId()).orElseThrow().getArchivedAt()).isEqualTo(first);
	}

	@Test
	void cannotArchiveAPlanInAnotherOrganisation() throws Exception {
		Project theirs = project(this.grace, "Umbrella's secret plan");

		archive(this.ada, theirs, "archive").andExpect(status().isNotFound());

		assertThat(this.projects.findById(theirs.getId()).orElseThrow().getArchivedAt()).isNull();
	}

	// Who may -----------------------------------------------------------------

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
	}

	/** An identity token names a person and no organisation, so it reaches no plan. */
	@Test
	void requiresATokenScopedToAnOrganisation() throws Exception {
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, "Bearer " + identity))
			.andExpect(status().isForbidden());
	}

	/**
	 * The reason every method re-reads the membership instead of trusting the token: an
	 * access token pins an organisation for twelve hours, so somebody removed this
	 * morning would otherwise go on reading its plans all day.
	 */
	@Test
	void refusesSomebodyRemovedFromTheOrganisationSinceTheirTokenWasIssued() throws Exception {
		project(this.ada, "Q3 platform work");
		String stale = bearer(this.bob);
		this.memberships.delete(this.bob);

		this.mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, stale))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	@Test
	void refusesToStartAPlanForAnOrganisationTheCallerHasLeft() throws Exception {
		String stale = bearer(this.bob);
		this.memberships.delete(this.bob);

		this.mvc
			.perform(post("/api/projects").header(HttpHeaders.AUTHORIZATION, stale)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new CreateProjectRequest("Q3 platform work", null))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));

		assertThat(this.projects.findAll()).isEmpty();
	}

	// Fixtures ----------------------------------------------------------------

	private ResultActions create(Membership caller, String name, String description) throws Exception {
		return this.mvc.perform(post("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new CreateProjectRequest(name, description))));
	}

	/** A body exactly as written, for the cases about what the server does to one. */
	private ResultActions createFromBody(String body) throws Exception {
		return this.mvc.perform(post("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions list(Membership caller) throws Exception {
		return this.mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private ResultActions listArchived(Membership caller) throws Exception {
		return this.mvc
			.perform(get("/api/projects").param("archived", "true").header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private ResultActions update(Membership caller, Project project, String name, String description) throws Exception {
		return this.mvc
			.perform(patch("/api/projects/" + project.getId()).header(HttpHeaders.AUTHORIZATION, bearer(caller))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new UpdateProjectRequest(name, description))));
	}

	private ResultActions archive(Membership caller, Project project, String action) throws Exception {
		return this.mvc.perform(post("/api/projects/" + project.getId() + "/" + action)
			.header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private Project project(Membership owner, String name) {
		return this.projects.save(new Project(owner.getTenant(), name, null, CREATED_AT));
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
