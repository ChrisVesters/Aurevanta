package com.cvesters.aurevanta.item;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.IntStream;

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
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectRepository;
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
 * A plan that can be typed in, with no numbers on it yet.
 *
 * <p>
 * Two organisations again, each with a plan of its own, because the failure this fixture
 * exists to catch is an item reached from outside the organisation that owns it — and a
 * single-tenant fixture cannot tell a scoped query from an unscoped one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WorkItemApiTests {

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
	private WorkItemRepository items;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	/** Owns Acme. */
	private Membership ada;

	/** Belongs to Acme without owning it, and may do everything here. */
	private Membership bob;

	/** Owns Umbrella, and is who a leak would leak to. */
	private Membership grace;

	private Project acmePlan;

	private Project umbrellaPlan;

	@BeforeEach
	void seedTwoOrganisationsWithAPlanEach() {
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.users.deleteAll();
		this.tenants.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.bob = member(user("bob@acme.test", "Bob"), acme, UserRole.MEMBER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		this.acmePlan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
		this.umbrellaPlan = this.projects.save(new Project(umbrella, "Their plan", null, CREATED_AT));
	}

	// Writing work down -------------------------------------------------------

	@Test
	void aMemberCanWriteWorkDown() throws Exception {
		create(this.bob, this.acmePlan, "Migrate the auth service", "Blocked on the vendor")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.projectId").value(this.acmePlan.getId().toString()))
			.andExpect(jsonPath("$.title").value("Migrate the auth service"))
			.andExpect(jsonPath("$.description").value("Blocked on the vendor"))
			.andExpect(jsonPath("$.archivedAt").value(nullValue()));
	}

	/**
	 * The denormalised {@code tenant_id} is what every query here is scoped by, so it has
	 * to come off the plan rather than from anything the caller could have got wrong.
	 */
	@Test
	void anItemBelongsToOneProjectAndOneOrganisation() throws Exception {
		create(this.ada, this.acmePlan, "Migrate the auth service", null).andExpect(status().isCreated());

		assertThat(this.items.findAllInProject(this.ada.getTenant().getId(), this.acmePlan.getId(), false))
			.singleElement()
			.satisfies((item) -> assertThat(item.getProject().getId()).isEqualTo(this.acmePlan.getId()),
					(item) -> assertThat(item.getTenant().getId()).isEqualTo(this.ada.getTenant().getId()));
	}

	/**
	 * Entering a plan is mostly typing a list of things; prose about each is optional.
	 */
	@Test
	void aDescriptionIsOptional() throws Exception {
		create(this.ada, this.acmePlan, "Migrate the auth service", null).andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").value(nullValue()));
	}

	@Test
	void twoItemsMayShareATitle() throws Exception {
		create(this.ada, this.acmePlan, "Write the migration", null).andExpect(status().isCreated());

		create(this.ada, this.acmePlan, "Write the migration", null).andExpect(status().isCreated());

		assertThat(this.items.findAll()).hasSize(2);
	}

	@Test
	void rejectsAnItemWithNoTitle() throws Exception {
		create(this.ada, this.acmePlan, "  ", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title.code").value("not_blank"));

		assertThat(this.items.findAll()).isEmpty();
	}

	@Test
	void rejectsATitleLongerThanTheColumn() throws Exception {
		create(this.ada, this.acmePlan, "x".repeat(201), null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title.code").value("max_size"))
			.andExpect(jsonPath("$.errors.title.max").value(200));
	}

	@Test
	void rejectsADescriptionLongerThanTheColumn() throws Exception {
		create(this.ada, this.acmePlan, "Migrate the auth service", "x".repeat(2001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.description.code").value("max_size"));
	}

	/**
	 * Normalised before validation, and written by hand rather than serialised from the
	 * record — which would strip it here and leave the server nothing to prove.
	 */
	@Test
	void stripsWhatWasTypedBeforeItIsJudged() throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/items")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"  Migrate the auth service  \",\"description\":\"   \"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Migrate the auth service"))
			.andExpect(jsonPath("$.description").value(nullValue()));
	}

	@Test
	void rejectsARequestWithNoTitleAtAll() throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/items")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title.code").value("not_blank"));
	}

	/**
	 * Archiving a plan says it is not being worked from, not that it is sealed. Somebody
	 * tidying up an old plan should not be refused for it.
	 */
	@Test
	void anArchivedProjectStillAcceptsWork() throws Exception {
		this.acmePlan.archive(CREATED_AT);
		this.projects.save(this.acmePlan);

		create(this.ada, this.acmePlan, "One we forgot", null).andExpect(status().isCreated());
	}

	// Reading it back ---------------------------------------------------------

	@Test
	void listsTheWorkInOneProjectInTheOrderItWasWrittenDown() throws Exception {
		item("First", CREATED_AT);
		item("Second", CREATED_AT.plusSeconds(60));
		item("Third", CREATED_AT.plusSeconds(120));

		list(this.bob, this.acmePlan).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].title").value("First"))
			.andExpect(jsonPath("$[1].title").value("Second"))
			.andExpect(jsonPath("$[2].title").value("Third"));
	}

	/** Work in one plan is not work in another, even inside one organisation. */
	@Test
	void listsNothingFromAnotherProject() throws Exception {
		item("Ours", CREATED_AT);
		Project second = this.projects.save(new Project(this.ada.getTenant(), "Another plan", null, CREATED_AT));

		list(this.ada, second).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	/**
	 * The ceiling the milestone fixed, exercised rather than assumed: it is what decides
	 * that this endpoint needs no pagination and that M3 can forecast in a request.
	 */
	@Test
	void fiveHundredItemsLoadInOneResponse() throws Exception {
		this.items.saveAll(IntStream.range(0, 500)
			.mapToObj((n) -> new WorkItem(this.acmePlan, "Item " + n, null, CREATED_AT.plusSeconds(n)))
			.toList());

		list(this.ada, this.acmePlan).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(500));
	}

	/**
	 * "No such plan" and "a plan with nothing in it" are different answers, and only one
	 * of them is worth acting on.
	 */
	@Test
	void listingWorkInAPlanThatIsNotThereIsRefusedRatherThanEmpty() throws Exception {
		this.mvc
			.perform(get("/api/projects/" + UUID.randomUUID() + "/items").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	/**
	 * The leak read from the other side: Grace asks a question she is entitled to ask,
	 * and Acme's work is not in the answer.
	 */
	@Test
	void oneOrganisationSeesNoneOfAnothersWork() throws Exception {
		item("Ours", CREATED_AT);

		list(this.grace, this.umbrellaPlan).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void cannotListTheWorkInAnotherOrganisationsPlan() throws Exception {
		list(this.ada, this.umbrellaPlan).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	@Test
	void cannotAddWorkToAnotherOrganisationsPlan() throws Exception {
		create(this.ada, this.umbrellaPlan, "Ours now", null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));

		assertThat(this.items.findAll()).isEmpty();
	}

	// Changing one ------------------------------------------------------------

	@Test
	void aMemberCanRewordAnItem() throws Exception {
		WorkItem item = item("Migrate the auth service", CREATED_AT);

		update(this.bob, item, "Migrate auth", "Vendor unblocked").andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Migrate auth"))
			.andExpect(jsonPath("$.description").value("Vendor unblocked"))
			// The plan it belongs to is not something rewording it can move.
			.andExpect(jsonPath("$.projectId").value(this.acmePlan.getId().toString()));
	}

	@Test
	void aDescriptionCanBeCleared() throws Exception {
		WorkItem item = this.items.save(new WorkItem(this.acmePlan, "Migrate auth", "Blocked", CREATED_AT));

		update(this.ada, item, "Migrate auth", null).andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value(nullValue()));

		assertThat(this.items.findById(item.getId()).orElseThrow().getDescription()).isNull();
	}

	@Test
	void rejectsAChangeThatTitlesNothing() throws Exception {
		WorkItem item = item("Migrate auth", CREATED_AT);

		update(this.ada, item, " ", null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title.code").value("not_blank"));

		assertThat(this.items.findById(item.getId()).orElseThrow().getTitle()).isEqualTo("Migrate auth");
	}

	@Test
	void rejectsAChangeWithNoTitleAtAll() throws Exception {
		WorkItem item = item("Migrate auth", CREATED_AT);

		this.mvc
			.perform(patch("/api/items/" + item.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.title.code").value("not_blank"));
	}

	/**
	 * An identifier from another organisation answers exactly as one that never existed,
	 * which is what stops this being a way to discover what exists elsewhere.
	 */
	@Test
	void anItemInAnotherOrganisationIsNotFound() throws Exception {
		WorkItem theirs = this.items.save(new WorkItem(this.umbrellaPlan, "Their work", null, CREATED_AT));

		update(this.ada, theirs, "Ours now", null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));

		assertThat(this.items.findById(theirs.getId()).orElseThrow().getTitle()).isEqualTo("Their work");
	}

	@Test
	void anIdentifierNothingHasIsTheSameAnswer() throws Exception {
		this.mvc
			.perform(patch("/api/items/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new UpdateWorkItemRequest("Anything", null))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));
	}

	// Putting one away --------------------------------------------------------

	@Test
	void archivingKeepsAnItemOutOfTheDefaultListing() throws Exception {
		WorkItem item = item("Migrate auth", CREATED_AT);
		item("Still to do", CREATED_AT.plusSeconds(60));

		archive(this.bob, item, "archive").andExpect(status().isOk()).andExpect(jsonPath("$.archivedAt").isNotEmpty());

		list(this.ada, this.acmePlan).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("Still to do"));
		listArchived(this.ada, this.acmePlan).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("Migrate auth"));
		// Nothing is destroyed: from step 3 this row is what an estimate hangs off.
		assertThat(this.items.findAll()).hasSize(2);
	}

	@Test
	void unarchivingBringsItBack() throws Exception {
		WorkItem item = item("Migrate auth", CREATED_AT);
		archive(this.ada, item, "archive").andExpect(status().isOk());

		archive(this.ada, item, "unarchive").andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").value(nullValue()));

		list(this.ada, this.acmePlan).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void archivingTwiceKeepsTheMomentItWasFirstPutAway() throws Exception {
		WorkItem item = item("Migrate auth", CREATED_AT);
		archive(this.ada, item, "archive").andExpect(status().isOk());
		Instant first = this.items.findById(item.getId()).orElseThrow().getArchivedAt();

		archive(this.ada, item, "archive").andExpect(status().isOk());

		assertThat(this.items.findById(item.getId()).orElseThrow().getArchivedAt()).isEqualTo(first);
	}

	@Test
	void cannotArchiveAnItemInAnotherOrganisation() throws Exception {
		WorkItem theirs = this.items.save(new WorkItem(this.umbrellaPlan, "Their work", null, CREATED_AT));

		archive(this.ada, theirs, "archive").andExpect(status().isNotFound());

		assertThat(this.items.findById(theirs.getId()).orElseThrow().getArchivedAt()).isNull();
	}

	// Who may -----------------------------------------------------------------

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/items")).andExpect(status().isUnauthorized());
	}

	@Test
	void requiresATokenScopedToAnOrganisation() throws Exception {
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/items").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + identity))
			.andExpect(status().isForbidden());
	}

	/**
	 * The membership is re-read on the item endpoints too, which name no project and so
	 * would otherwise trust a token for the whole of its twelve hours.
	 */
	@Test
	void refusesSomebodyRemovedFromTheOrganisationSinceTheirTokenWasIssued() throws Exception {
		WorkItem item = item("Migrate auth", CREATED_AT);
		String stale = bearer(this.bob);
		this.memberships.delete(this.bob);

		this.mvc
			.perform(patch("/api/items/" + item.getId()).header(HttpHeaders.AUTHORIZATION, stale)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new UpdateWorkItemRequest("Theirs now", null))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));

		assertThat(this.items.findById(item.getId()).orElseThrow().getTitle()).isEqualTo("Migrate auth");
	}

	// Fixtures ----------------------------------------------------------------

	private ResultActions create(Membership caller, Project project, String title, String description)
			throws Exception {
		return this.mvc.perform(
				post("/api/projects/" + project.getId() + "/items").header(HttpHeaders.AUTHORIZATION, bearer(caller))
					.contentType(MediaType.APPLICATION_JSON)
					.content(this.json.writeValueAsString(new CreateWorkItemRequest(title, description))));
	}

	private ResultActions list(Membership caller, Project project) throws Exception {
		return this.mvc.perform(
				get("/api/projects/" + project.getId() + "/items").header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private ResultActions listArchived(Membership caller, Project project) throws Exception {
		return this.mvc.perform(get("/api/projects/" + project.getId() + "/items").param("archived", "true")
			.header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private ResultActions update(Membership caller, WorkItem item, String title, String description) throws Exception {
		return this.mvc.perform(patch("/api/items/" + item.getId()).header(HttpHeaders.AUTHORIZATION, bearer(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(new UpdateWorkItemRequest(title, description))));
	}

	private ResultActions archive(Membership caller, WorkItem item, String action) throws Exception {
		return this.mvc.perform(
				post("/api/items/" + item.getId() + "/" + action).header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private WorkItem item(String title, Instant createdAt) {
		return this.items.save(new WorkItem(this.acmePlan, title, null, createdAt));
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
