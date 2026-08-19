package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What has already happened, so a forecast can leave it out rather than predict it again.
 *
 * <p>
 * The cases worth reading are the refusals and the clearing. A date the server invented
 * would be indistinguishable, to calibration and the reporting layer, from one somebody
 * reported — and a start date left behind on work that has been put back to not-started
 * would be evidence of something that did not happen.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WorkItemProgressApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-13T08:00:00Z");

	private static final LocalDate STARTED = LocalDate.parse("2026-08-10");

	private static final LocalDate FINISHED = LocalDate.parse("2026-08-14");

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

	private Membership ada;

	private Membership grace;

	private WorkItem migration;

	@BeforeEach
	void seedAPlanWithWorkInIt() {
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		Project acmePlan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
		this.migration = this.items.save(new WorkItem(acmePlan, "Migrate the auth service", null, CREATED_AT));
	}

	@Test
	void workStartsOutHavingNotStarted() throws Exception {
		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getStatus())
			.isEqualTo(WorkItemStatus.NOT_STARTED);
	}

	// Each transition ---------------------------------------------------------

	@Test
	void workCanBePickedUp() throws Exception {
		progress(this.ada, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.startedOn").value("2026-08-10"))
			.andExpect(jsonPath("$.completedOn").value(nullValue()));
	}

	@Test
	void workCanBeFinished() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, FINISHED, new BigDecimal("6.5")).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.startedOn").value("2026-08-10"))
			.andExpect(jsonPath("$.completedOn").value("2026-08-14"))
			.andExpect(jsonPath("$.actualEffortHours").value(6.5));
	}

	/**
	 * Ticked off by somebody who never marked it as begun, which is the commonest way
	 * anything gets recorded — and refusing it would be refusing the common case.
	 */
	@Test
	void workCanBeFinishedWithoutEverHavingBeenPickedUp() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, null, FINISHED, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.startedOn").value(nullValue()));
	}

	/**
	 * calibration wants it and most teams do not measure it, so it cannot be the price of
	 * finishing.
	 */
	@Test
	void actualEffortIsOptionalOnFinishedWork() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, FINISHED, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.actualEffortHours").value(nullValue()));
	}

	/**
	 * Taking a claim back: the request carries nothing, so the row keeps nothing. What it
	 * must not do is keep a start date on work that has not started, which would be
	 * evidence of something that did not happen.
	 */
	@Test
	void puttingWorkBackToNotStartedLeavesNothingBehind() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, FINISHED, new BigDecimal("6.5")).andExpect(status().isOk());

		progress(this.ada, WorkItemStatus.NOT_STARTED, null, null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("NOT_STARTED"))
			.andExpect(jsonPath("$.startedOn").value(nullValue()))
			.andExpect(jsonPath("$.completedOn").value(nullValue()))
			.andExpect(jsonPath("$.actualEffortHours").value(nullValue()));

		WorkItem stored = this.items.findById(this.migration.getId()).orElseThrow();
		assertThat(stored.getStartedOn()).isNull();
		assertThat(stored.getCompletedOn()).isNull();
		assertThat(stored.getActualEffortHours()).isNull();
	}

	/** Something under way has not been finished, whatever the row said a moment ago. */
	@Test
	void reopeningFinishedWorkClearsTheCompletion() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, FINISHED, null).andExpect(status().isOk());

		progress(this.ada, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.startedOn").value("2026-08-10"))
			.andExpect(jsonPath("$.completedOn").value(nullValue()));
	}

	/** Work under way may well have taken hours already; it simply has not finished. */
	@Test
	void workInProgressCanCarryTheEffortSpentSoFar() throws Exception {
		progress(this.ada, WorkItemStatus.IN_PROGRESS, STARTED, null, new BigDecimal("4")).andExpect(status().isOk())
			.andExpect(jsonPath("$.actualEffortHours").value(4.0));
	}

	// Each refusal ------------------------------------------------------------

	@Test
	void refusesWorkInProgressWithNoStartDate() throws Exception {
		progress(this.ada, WorkItemStatus.IN_PROGRESS, null, null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_date_required"));

		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getStatus())
			.isEqualTo(WorkItemStatus.NOT_STARTED);
	}

	@Test
	void refusesFinishedWorkWithNoCompletionDate() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_date_required"));
	}

	/**
	 * <strong>The refusal that replaced a silent drop.</strong> Keeping what the status
	 * allows and discarding the rest reads as careful and is not: somebody records four
	 * hours against work marked not started, the form closes, and the number is gone with
	 * nothing said about it.
	 */
	@Test
	void refusesAnEffortOnWorkThatHasNotStarted() throws Exception {
		progress(this.ada, WorkItemStatus.NOT_STARTED, null, null, new BigDecimal("4"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_not_applicable"));

		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getActualEffortHours()).isNull();
	}

	@Test
	void refusesAStartDateOnWorkThatHasNotStarted() throws Exception {
		progress(this.ada, WorkItemStatus.NOT_STARTED, STARTED, null, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_not_applicable"));
	}

	@Test
	void refusesACompletionDateOnWorkThatHasNotStarted() throws Exception {
		progress(this.ada, WorkItemStatus.NOT_STARTED, null, FINISHED, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_not_applicable"));
	}

	/**
	 * Work that is under way has not been finished, so there is no date it finished on.
	 */
	@Test
	void refusesACompletionDateOnWorkThatIsStillInProgress() throws Exception {
		progress(this.ada, WorkItemStatus.IN_PROGRESS, STARTED, FINISHED, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_not_applicable"));

		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getCompletedOn()).isNull();
	}

	@Test
	void refusesWorkThatFinishedBeforeItBegan() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, FINISHED, STARTED, null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_out_of_order"));
	}

	/** Started and finished on one day is an ordinary day's work, not a contradiction. */
	@Test
	void acceptsWorkThatStartedAndFinishedOnTheSameDay() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, FINISHED, FINISHED, null).andExpect(status().isOk());
	}

	@Test
	void refusesARequestWithNoStatus() throws Exception {
		this.mvc
			.perform(patch("/api/items/" + this.migration.getId() + "/progress")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.status.code").value("not_null"));
	}

	@Test
	void refusesAStatusThatIsNotOneOfTheThree() throws Exception {
		this.mvc
			.perform(patch("/api/items/" + this.migration.getId() + "/progress")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"ABANDONED\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void refusesAnEffortOfNothing() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, FINISHED, BigDecimal.ZERO).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.actualEffortHours.code").value("positive"));
	}

	/** Finer than the column keeps is refused rather than rounded, as an estimate is. */
	@Test
	void refusesMorePrecisionThanTheColumnKeeps() throws Exception {
		progress(this.ada, WorkItemStatus.DONE, STARTED, FINISHED, new BigDecimal("0.005"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.actualEffortHours.code").value("digits"));
	}

	/**
	 * The dates are checked before the item is looked up, so nonsense tells a caller
	 * nothing about which items exist.
	 */
	@Test
	void refusesAnUnsupportedClaimWithoutSayingWhetherTheItemExists() throws Exception {
		this.mvc
			.perform(patch("/api/items/" + UUID.randomUUID() + "/progress")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("progress_date_required"));
	}

	// Whose work it is --------------------------------------------------------

	@Test
	void cannotRecordProgressOnWorkInAnotherOrganisation() throws Exception {
		progress(this.grace, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));

		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getStatus())
			.isEqualTo(WorkItemStatus.NOT_STARTED);
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc
			.perform(patch("/api/items/" + this.migration.getId() + "/progress").contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"NOT_STARTED\"}"))
			.andExpect(status().isUnauthorized());
	}

	// Fixtures ----------------------------------------------------------------

	private ResultActions progress(Membership caller, WorkItemStatus status, LocalDate startedOn, LocalDate completedOn,
			BigDecimal actualEffortHours) throws Exception {
		UpdateProgressRequest request = new UpdateProgressRequest(status, startedOn, completedOn, actualEffortHours);
		return this.mvc.perform(patch("/api/items/" + this.migration.getId() + "/progress")
			.header(HttpHeaders.AUTHORIZATION, bearer(caller))
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(request)));
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
