package com.cvesters.aurevanta.requirement;

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
import com.cvesters.aurevanta.resource.Resource;
import com.cvesters.aurevanta.resource.ResourceRepository;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the work in a plan needs.
 *
 * <p>
 * <strong>A whole set at a time and never one line of it</strong>, which is what the one
 * {@code PUT} in this application is for. The case worth reading first is
 * {@link #aWholeSetReplacesWhatWasThereBefore}: everything else in this class is a
 * property of that shape.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RequirementApiTests {

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
	private ProjectRepository projects;

	@Autowired
	private WorkItemRepository items;

	@Autowired
	private ResourceRepository resources;

	@Autowired
	private RequirementRepository requirements;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership grace;

	private Project plan;

	private WorkItem migration;

	private WorkItem rollout;

	private Resource backend;

	private Resource staging;

	private Resource theirs;

	@BeforeEach
	void seedAPlanAndATeam() {
		this.requirements.deleteAll();
		this.resources.deleteAll();
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		this.plan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
		this.migration = this.items.save(new WorkItem(this.plan, "Migrate the auth service", null, CREATED_AT));
		this.rollout = this.items.save(new WorkItem(this.plan, "Roll it out", null, CREATED_AT.plusSeconds(1)));
		this.backend = this.resources.save(new Resource(acme, "Backend engineers", 3, null, CREATED_AT));
		this.staging = this.resources
			.save(new Resource(acme, "Staging environment", 1, null, CREATED_AT.plusSeconds(1)));
		this.theirs = this.resources.save(new Resource(umbrella, "Containment team", 2, null, CREATED_AT));
	}

	// The shape everything else rests on ----------------------------------------

	/**
	 * <strong>A piece of work needs a set, and the set arrives at once.</strong> Sending
	 * a second one replaces the first rather than adding to it, which is what makes "it
	 * needs these two things now" one fact rather than a sequence a reader has to
	 * reassemble.
	 */
	@Test
	void aWholeSetReplacesWhatWasThereBefore() throws Exception {
		needs(this.migration, needing(this.backend, 1), needing(this.staging, 1)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));

		needs(this.migration, needing(this.backend, 2)).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].resourceId").value(this.backend.getId().toString()))
			.andExpect(jsonPath("$[0].units").value(2));
	}

	/**
	 * The example the plan's own <em>done when</em> asks for: a task that needs a backend
	 * engineer and the staging environment, read back with the names a screen labels them
	 * by.
	 */
	@Test
	void aTaskCanNeedAnEngineerAndAnEnvironment() throws Exception {
		needs(this.migration, needing(this.backend, 1), needing(this.staging, 1));

		this.mvc
			.perform(get("/api/items/" + this.migration.getId() + "/requirements").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].resourceName").value("Backend engineers"))
			.andExpect(jsonPath("$[0].units").value(1))
			.andExpect(jsonPath("$[1].resourceName").value("Staging environment"))
			.andExpect(jsonPath("$[1].resourceArchived").value(false));
	}

	/**
	 * <strong>An empty set is a claim rather than an omission.</strong> It says this is
	 * generic work anybody can pick up, which the scheduler will read as one unit of
	 * whichever pool has one free — a different thing from work that is outside the
	 * competition for a team altogether.
	 */
	@Test
	void needingNothingIsSomethingSomebodyCanSay() throws Exception {
		needs(this.migration, needing(this.backend, 1));

		needs(this.migration).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	/**
	 * A screen showing a plan reads every line at once, which is {@code estimate}'s rule:
	 * asking per item would be five hundred requests to draw one page.
	 */
	@Test
	void awholePlansRequirementsAreOneRequest() throws Exception {
		needs(this.migration, needing(this.backend, 1));
		needs(this.rollout, needing(this.staging, 1));

		this.mvc
			.perform(get("/api/projects/" + this.plan.getId() + "/requirements").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].workItemId").value(this.migration.getId().toString()))
			.andExpect(jsonPath("$[1].workItemId").value(this.rollout.getId().toString()));
	}

	/** "No such plan" and "a plan needing nothing" are different answers. */
	@Test
	void aPlanThatIsNotThereIsNotAnEmptyList() throws Exception {
		this.mvc
			.perform(get("/api/projects/" + UUID.randomUUID() + "/requirements").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	// What it refuses ------------------------------------------------------------

	/**
	 * <strong>Two lines for one pool are two spellings of one number</strong>, and a
	 * scheduler adding them up would read a data-entry mistake as a claim about a team.
	 * Decided from the request alone, before anything is looked up, the way a
	 * self-dependency is.
	 */
	@Test
	void onePoolCannotBeNamedTwice() throws Exception {
		needs(this.migration, needing(this.backend, 1), needing(this.backend, 2)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("duplicate_requirement"));
	}

	/**
	 * <strong>Work needing more of a pool than the pool holds never starts</strong> — in
	 * any run, at any moment — so a plan holding it has no schedule rather than a
	 * pessimistic one. Refused here where the person can see the bound: every box on that
	 * form carries how many the pool has.
	 */
	@Test
	void workCannotNeedMoreOfAPoolThanThePoolHolds() throws Exception {
		needs(this.migration, needing(this.staging, 2)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("requirement_exceeds_pool"));
	}

	/** Exactly what it holds is not more than it holds. */
	@Test
	void workMayNeedEveryUnitAPoolHolds() throws Exception {
		needs(this.migration, needing(this.backend, 3)).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].units").value(3));
	}

	/** And nothing was written on the way to refusing it. */
	@Test
	void aRefusedSetLeavesWhatWasThereAlone() throws Exception {
		needs(this.migration, needing(this.backend, 1));

		needs(this.migration, needing(this.staging, 1), needing(this.staging, 1)).andExpect(status().isBadRequest());

		this.mvc
			.perform(get("/api/items/" + this.migration.getId() + "/requirements").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].resourceId").value(this.backend.getId().toString()));
	}

	/**
	 * A pool in another organisation is not there at all, which is the isolation rule: an
	 * identifier from a request can only ever select something the caller's own
	 * organisation owns.
	 */
	@Test
	void aPoolFromAnotherOrganisationIsNotFound() throws Exception {
		needs(this.migration, needing(this.theirs, 1)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("resource_not_found"));
	}

	@Test
	void needingNoneOfAPoolIsRefused() throws Exception {
		String body = "{\"needs\":[{\"resourceId\":\"" + this.backend.getId() + "\",\"units\":0}]}";

		send(this.migration, body).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['needs[0].units'].code").value("positive"));
	}

	@Test
	void aRequirementNamingNoPoolIsRefused() throws Exception {
		send(this.migration, "{\"needs\":[{\"units\":2}]}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors['needs[0].resourceId'].code").value("not_null"));
	}

	/**
	 * The list itself is required: an absent one is not the same claim as an empty one.
	 */
	@Test
	void theSetItselfIsRequired() throws Exception {
		send(this.migration, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.needs.code").value("not_null"));
	}

	@Test
	void anotherOrganisationsWorkIsNotThere() throws Exception {
		this.mvc
			.perform(put("/api/items/" + this.migration.getId() + "/requirements")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.grace))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"needs\":[]}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/items/" + this.migration.getId() + "/requirements"))
			.andExpect(status().isUnauthorized());
	}

	// Archived pools -------------------------------------------------------------

	/**
	 * <strong>A pool put away is still a pool a plan can name.</strong> Archiving says a
	 * team no longer has something, and a plan still asking for it is worth forecasting
	 * honestly rather than refusing — the requirement says the pool is gone, so nothing
	 * downstream has to guess.
	 */
	@Test
	void workMayStillNeedAPoolThatHasBeenPutAway() throws Exception {
		needs(this.migration, needing(this.staging, 1));
		this.staging.archive(CREATED_AT);
		this.resources.save(this.staging);

		this.mvc
			.perform(get("/api/items/" + this.migration.getId() + "/requirements").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].resourceArchived").value(true))
			.andExpect(jsonPath("$[0].resourceName").value("Staging environment"));
	}

	// Fixtures -------------------------------------------------------------------

	private static String needing(Resource resource, int units) {
		return "{\"resourceId\":\"" + resource.getId() + "\",\"units\":" + units + "}";
	}

	private ResultActions needs(WorkItem item, String... lines) throws Exception {
		return send(item, "{\"needs\":[" + String.join(",", lines) + "]}");
	}

	private ResultActions send(WorkItem item, String body) throws Exception {
		return this.mvc.perform(
				put("/api/items/" + item.getId() + "/requirements").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body));
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
