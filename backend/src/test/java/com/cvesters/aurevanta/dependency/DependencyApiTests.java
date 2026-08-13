package com.cvesters.aurevanta.dependency;

import java.math.BigDecimal;
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
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemRepository;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A plan with a shape, which is the difference between a queue and something worth
 * forecasting.
 *
 * <p>
 * Two organisations again, and this time two plans inside one of them as well: the
 * failures worth catching here are an edge reaching an item in somebody else's
 * organisation, and an edge joining two plans that merely happen to share an owner.
 * Neither is visible in a fixture with one of anything.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DependencyApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-13T08:00:00Z");

	private static final BigDecimal NO_LAG = BigDecimal.ZERO;

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
	private DependencyRepository dependencies;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	/** Owns Acme. */
	private Membership ada;

	/** Belongs to Acme without owning it, and may draw every arrow Ada may. */
	private Membership bob;

	/** Owns Umbrella, and is who a leak would leak to. */
	private Membership grace;

	private Project acmePlan;

	/** A second plan in the same organisation, which is where a stray edge would go. */
	private Project acmeOtherPlan;

	private Project umbrellaPlan;

	private WorkItem design;

	private WorkItem build;

	private WorkItem ship;

	@BeforeEach
	void seedTwoOrganisationsAndThreePlans() {
		this.dependencies.deleteAll();
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
		this.acmeOtherPlan = this.projects.save(new Project(acme, "Q4 platform work", null, CREATED_AT));
		this.umbrellaPlan = this.projects.save(new Project(umbrella, "Their plan", null, CREATED_AT));
		this.design = item(this.acmePlan, "Design the migration");
		this.build = item(this.acmePlan, "Build the migration");
		this.ship = item(this.acmePlan, "Ship it");
	}

	// Drawing an arrow --------------------------------------------------------

	@Test
	void aMemberCanSayOnePieceOfWorkComesBeforeAnother() throws Exception {
		create(this.bob, this.design, this.build, NO_LAG).andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.predecessorItemId").value(this.design.getId().toString()))
			.andExpect(jsonPath("$.successorItemId").value(this.build.getId().toString()))
			.andExpect(jsonPath("$.lagHours").value(0));
	}

	/**
	 * A wait between the two is the whole of what this row carries beyond the arrow
	 * itself, so it has to survive the round trip rather than be quietly rounded away.
	 */
	@Test
	void anArrowCanCarryAWait() throws Exception {
		create(this.ada, this.design, this.build, new BigDecimal("16.50")).andExpect(status().isCreated())
			.andExpect(jsonPath("$.lagHours").value(16.5));
	}

	/**
	 * The denormalised {@code tenant_id} and {@code project_id} are what every query here
	 * is scoped by, so they have to come off the work rather than from anything a caller
	 * could have got wrong.
	 */
	@Test
	void anArrowBelongsToOnePlanAndOneOrganisation() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());

		assertThat(this.dependencies.findAllInProject(this.ada.getTenant().getId(), this.acmePlan.getId()))
			.singleElement()
			.satisfies((edge) -> assertThat(edge.getPredecessor().getId()).isEqualTo(this.design.getId()),
					(edge) -> assertThat(edge.getSuccessor().getId()).isEqualTo(this.build.getId()));
	}

	// What is refused ---------------------------------------------------------

	/**
	 * The shortest cycle there is, and the only one answerable without reading a row —
	 * which is why it is answered first.
	 */
	@Test
	void refusesWorkThatWouldWaitForItself() throws Exception {
		create(this.ada, this.design, this.design, NO_LAG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("self_dependency"));

		assertThat(this.dependencies.findAll()).isEmpty();
	}

	/**
	 * Answered before anything is looked up, so an identifier nobody has ever issued gets
	 * the same refusal as a real one. A caller who put the same value in both boxes
	 * learns nothing about which items exist by being told so.
	 */
	@Test
	void refusesWorkWaitingForItselfWithoutSayingWhetherItExists() throws Exception {
		UUID nobodys = UUID.randomUUID();

		this.mvc
			.perform(post("/api/dependencies").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new CreateDependencyRequest(nobodys, nobodys, NO_LAG))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("self_dependency"));
	}

	/**
	 * The two-item loop: B already waits for A, and A is asked to wait for B. The path
	 * names both, in the order the loop would have run.
	 */
	@Test
	void refusesATwoItemCycleAndSaysWhichLoop() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());

		create(this.ada, this.build, this.design, NO_LAG).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dependency_cycle"))
			.andExpect(jsonPath("$.path", contains(this.build.getId().toString(), this.design.getId().toString())));

		assertThat(this.dependencies.findAll()).hasSize(1);
	}

	/**
	 * The same thing at a distance, which is the case a walk exists for: nothing about
	 * the proposed arrow's own two ends says anything is wrong with it.
	 */
	@Test
	void refusesAThreeItemCycleAndSaysWhichLoop() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());
		create(this.ada, this.build, this.ship, NO_LAG).andExpect(status().isCreated());

		create(this.ada, this.ship, this.design, NO_LAG).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dependency_cycle"))
			.andExpect(jsonPath("$.path", contains(this.ship.getId().toString(), this.design.getId().toString(),
					this.build.getId().toString())));

		assertThat(this.dependencies.findAll()).hasSize(2);
	}

	/**
	 * A diamond is not a cycle, and refusing it would be refusing the shape merge bias
	 * comes from — two strands of work that split and come back together is the single
	 * most ordinary thing a plan does.
	 */
	@Test
	void acceptsADiamond() throws Exception {
		WorkItem docs = item(this.acmePlan, "Write the runbook");
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());
		create(this.ada, this.design, docs, NO_LAG).andExpect(status().isCreated());
		create(this.ada, this.build, this.ship, NO_LAG).andExpect(status().isCreated());

		create(this.ada, docs, this.ship, NO_LAG).andExpect(status().isCreated());

		assertThat(this.dependencies.findAll()).hasSize(4);
	}

	/**
	 * The diamond closed back on itself, which is the case that tells a walk from a
	 * guess: the plan reaches the last item by two routes, so an implementation that
	 * followed one of them and stopped would accept the loop.
	 */
	@Test
	void refusesAnArrowThatWouldCloseADiamondBackOnItself() throws Exception {
		WorkItem docs = item(this.acmePlan, "Write the runbook");
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());
		create(this.ada, this.design, docs, NO_LAG).andExpect(status().isCreated());
		create(this.ada, this.build, this.ship, NO_LAG).andExpect(status().isCreated());
		create(this.ada, docs, this.ship, NO_LAG).andExpect(status().isCreated());

		create(this.ada, this.ship, this.design, NO_LAG).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dependency_cycle"));

		assertThat(this.dependencies.findAll()).hasSize(4);
	}

	/**
	 * Two rows meaning one thing is how a graph walk comes to count one path twice. Not
	 * reported as a cycle: the plan is already exactly as the caller wants it.
	 */
	@Test
	void refusesAnArrowThatHasAlreadyBeenDrawn() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());

		create(this.ada, this.design, this.build, new BigDecimal("4.00")).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("dependency_already_exists"));

		assertThat(this.dependencies.findAll()).hasSize(1);
	}

	/**
	 * Same organisation, different plan. A forecast is taken over one plan, so an edge
	 * leaving it would be a constraint the scheduler could not see — and the row has one
	 * {@code project_id} with no honest value for it.
	 */
	@Test
	void refusesAnArrowBetweenTwoPlans() throws Exception {
		WorkItem elsewhere = item(this.acmeOtherPlan, "Next quarter's problem");

		create(this.ada, this.design, elsewhere, NO_LAG).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("dependency_across_projects"));

		assertThat(this.dependencies.findAll()).isEmpty();
	}

	/**
	 * An item in somebody else's organisation is answered as an item that does not exist,
	 * before the cross-plan check can say anything about it — otherwise the refusal would
	 * be a way of asking which identifiers are real elsewhere.
	 */
	@Test
	void cannotReachAnItemInAnotherOrganisation() throws Exception {
		WorkItem theirs = item(this.umbrellaPlan, "Their work");

		create(this.ada, this.design, theirs, NO_LAG).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));

		create(this.ada, theirs, this.design, NO_LAG).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));

		assertThat(this.dependencies.findAll()).isEmpty();
	}

	@Test
	void refusesAWaitThatRunsBackwards() throws Exception {
		create(this.ada, this.design, this.build, new BigDecimal("-1.00")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.lagHours.code").value("positive_or_zero"));

		assertThat(this.dependencies.findAll()).isEmpty();
	}

	/**
	 * Zero is a claim that there is no wait; nothing at all is a question nobody
	 * answered, and the server does not answer it for them.
	 */
	@Test
	void refusesAnArrowWithNoWaitStated() throws Exception {
		create(this.ada, this.design, this.build, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.lagHours.code").value("not_null"));
	}

	@Test
	void refusesAnArrowWithOnlyOneEnd() throws Exception {
		this.mvc
			.perform(post("/api/dependencies").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(new CreateDependencyRequest(this.design.getId(), null, NO_LAG))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.successorItemId.code").value("not_null"));
	}

	// Reading a plan's shape --------------------------------------------------

	@Test
	void listsTheArrowsInOnePlan() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());
		create(this.ada, this.build, this.ship, new BigDecimal("8.00")).andExpect(status().isCreated());

		list(this.bob, this.acmePlan).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].predecessorItemId").value(this.design.getId().toString()))
			.andExpect(jsonPath("$[1].successorItemId").value(this.ship.getId().toString()))
			.andExpect(jsonPath("$[1].lagHours").value(8.0));
	}

	/** A plan nobody has joined up has no edges, which is not the same as no plan. */
	@Test
	void listsNothingForAPlanNobodyHasJoinedUp() throws Exception {
		list(this.ada, this.acmePlan).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void doesNotListArrowsFromAnotherPlan() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());

		list(this.ada, this.acmeOtherPlan).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void cannotListTheArrowsOfAPlanInAnotherOrganisation() throws Exception {
		list(this.ada, this.umbrellaPlan).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	/** And the same in the direction a leak would actually run. */
	@Test
	void anotherOrganisationIsToldNothingOfThisOnesShape() throws Exception {
		create(this.ada, this.design, this.build, NO_LAG).andExpect(status().isCreated());

		list(this.grace, this.acmePlan).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	// Rubbing one out ---------------------------------------------------------

	@Test
	void aMemberCanRubAnArrowOut() throws Exception {
		Dependency edge = drawn(this.design, this.build);

		this.mvc
			.perform(delete("/api/dependencies/" + edge.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.bob)))
			.andExpect(status().isNoContent());

		assertThat(this.dependencies.findAll()).isEmpty();
	}

	/**
	 * Removing an arrow that was closing nothing off leaves the plan free to be joined up
	 * the other way round, which is the whole reason an edge is deleted rather than
	 * archived.
	 */
	@Test
	void anArrowRubbedOutStopsBlockingTheOppositeOne() throws Exception {
		Dependency edge = drawn(this.design, this.build);

		this.mvc
			.perform(delete("/api/dependencies/" + edge.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isNoContent());

		create(this.ada, this.build, this.design, NO_LAG).andExpect(status().isCreated());
	}

	@Test
	void cannotRubOutAnArrowInAnotherOrganisation() throws Exception {
		WorkItem first = item(this.umbrellaPlan, "Theirs, first");
		WorkItem second = item(this.umbrellaPlan, "Theirs, second");
		Dependency theirs = this.dependencies.save(new Dependency(first, second, NO_LAG, CREATED_AT));

		this.mvc
			.perform(delete("/api/dependencies/" + theirs.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("dependency_not_found"));

		assertThat(this.dependencies.findById(theirs.getId())).isPresent();
	}

	@Test
	void cannotRubOutAnArrowNobodyDrew() throws Exception {
		this.mvc
			.perform(delete("/api/dependencies/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("dependency_not_found"));
	}

	/**
	 * An edge means nothing without the work at its ends, so the foreign keys cascade.
	 * Asserted because a missing {@code on delete cascade} would not surface until
	 * something removed an organisation and tripped a constraint instead.
	 */
	@Test
	void discardsArrowsWhenTheOrganisationGoes() throws Exception {
		drawn(this.design, this.build);

		// Straight at the row the whole organisation hangs off, so what is being asserted
		// is the database's own cascade rather than anything the application did on its
		// way there.
		this.memberships.deleteAll();
		this.tenants.deleteById(this.ada.getTenant().getId());

		assertThat(this.dependencies.findAll()).isEmpty();
		assertThat(this.items.findAll()).isEmpty();
	}

	// Who may -----------------------------------------------------------------

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/dependencies"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresATokenScopedToAnOrganisation() throws Exception {
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/dependencies")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + identity)).andExpect(status().isForbidden());
	}

	/**
	 * {@code POST /api/dependencies} names no organisation at all, so without the
	 * membership being re-read it would trust a token for the whole of its twelve hours.
	 */
	@Test
	void refusesSomebodyRemovedFromTheOrganisationSinceTheirTokenWasIssued() throws Exception {
		String stale = bearer(this.bob);
		this.memberships.delete(this.bob);

		this.mvc
			.perform(post("/api/dependencies").header(HttpHeaders.AUTHORIZATION, stale)
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json
					.writeValueAsString(new CreateDependencyRequest(this.design.getId(), this.build.getId(), NO_LAG))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));

		assertThat(this.dependencies.findAll()).isEmpty();
	}

	// Fixtures ----------------------------------------------------------------

	private ResultActions create(Membership caller, WorkItem predecessor, WorkItem successor, BigDecimal lagHours)
			throws Exception {
		return this.mvc.perform(post("/api/dependencies").header(HttpHeaders.AUTHORIZATION, bearer(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json
				.writeValueAsString(new CreateDependencyRequest(predecessor.getId(), successor.getId(), lagHours))));
	}

	private ResultActions list(Membership caller, Project project) throws Exception {
		return this.mvc.perform(get("/api/projects/" + project.getId() + "/dependencies")
			.header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private Dependency drawn(WorkItem predecessor, WorkItem successor) {
		return this.dependencies.save(new Dependency(predecessor, successor, NO_LAG, CREATED_AT));
	}

	private WorkItem item(Project project, String title) {
		return this.items.save(new WorkItem(project, title, null, CREATED_AT));
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
