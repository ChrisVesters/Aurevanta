package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectRepository;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The history of what has been claimed about a piece of work, which until {@code V16} was
 * written over every time somebody made a claim.
 *
 * <p>
 * <strong>What this class is really testing is M8's exclusion rule.</strong> A hit rate
 * that refuses to score an estimate written after the work began is only as trustworthy
 * as the date it compares against, and that date used to be editable by anybody with no
 * trace. The cases worth reading are therefore the ones about a start date that moved:
 * the boundary must take the earliest claim, because moving a start later is the
 * direction that turns a report into a forecast and makes the number kinder than the
 * truth.
 *
 * <p>
 * The refusals live next door in {@code WorkItemProgressApiTests}; what they contribute
 * here is that a refused claim writes nothing, which is asserted below rather than
 * assumed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class WorkItemProgressLogApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-13T08:00:00Z");

	private static final LocalDate STARTED = LocalDate.parse("2026-08-10");

	private static final LocalDate EARLIER = LocalDate.parse("2026-08-03");

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
	private WorkItemProgressRepository reports;

	@Autowired
	private WorkItemService service;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership linus;

	private Membership grace;

	private Tenant acme;

	private WorkItem migration;

	private WorkItem rollout;

	@BeforeEach
	void seedAPlanWithWorkInIt() {
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		this.acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), this.acme, UserRole.OWNER);
		this.linus = member(user("linus@acme.test", "Linus"), this.acme, UserRole.MEMBER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		Project acmePlan = this.projects.save(new Project(this.acme, "Q3 platform work", null, CREATED_AT));
		this.migration = this.items.save(new WorkItem(acmePlan, "Migrate the auth service", null, CREATED_AT));
		this.rollout = this.items.save(new WorkItem(acmePlan, "Roll it out", null, CREATED_AT));
	}

	// Every claim is kept ------------------------------------------------------

	@Test
	void reportingProgressWritesDownWhoSaidIt() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, new BigDecimal("4"))
			.andExpect(status().isOk());

		List<WorkItemProgress> history = this.reports.findForItem(this.acme.getId(), this.migration.getId());
		assertThat(history).singleElement().satisfies((report) -> {
			assertThat(report.getReportedBy().getDisplayName()).isEqualTo("Ada");
			assertThat(report.getStatus()).isEqualTo(WorkItemStatus.IN_PROGRESS);
			assertThat(report.getStartedOn()).isEqualTo(STARTED);
			assertThat(report.getCompletedOn()).isNull();
			assertThat(report.getActualEffortHours()).isEqualByComparingTo("4");
			assertThat(report.getReportedAt()).isNotNull();
		});
	}

	/**
	 * The first claim stays exactly as it was made. This is the whole property — an
	 * estimate is immutable for the same reason, and a report that could be rewritten
	 * would leave M8 measuring against whatever somebody last found convenient.
	 */
	@Test
	void aSecondReportLeavesTheFirstWhereItIs() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());
		progress(this.linus, this.migration, WorkItemStatus.DONE, EARLIER, FINISHED, new BigDecimal("9"))
			.andExpect(status().isOk());

		List<WorkItemProgress> history = this.reports.findForItem(this.acme.getId(), this.migration.getId());
		assertThat(history).hasSize(2);
		assertThat(history.get(0).getReportedBy().getDisplayName()).isEqualTo("Linus");
		assertThat(history.get(1).getReportedBy().getDisplayName()).isEqualTo("Ada");
		assertThat(history.get(1).getStartedOn()).isEqualTo(STARTED);
		assertThat(history.get(1).getStatus()).isEqualTo(WorkItemStatus.IN_PROGRESS);
	}

	/**
	 * Somebody confirming what the row already said is still somebody saying it, and a
	 * table that decided which claims were worth keeping would be the behaviour this one
	 * exists to replace.
	 */
	@Test
	void aReportThatChangesNothingIsStillARecordOfSomebodySayingIt() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.DONE, STARTED, FINISHED, null).andExpect(status().isOk());
		progress(this.ada, this.migration, WorkItemStatus.DONE, STARTED, FINISHED, null).andExpect(status().isOk());

		assertThat(this.reports.findForItem(this.acme.getId(), this.migration.getId())).hasSize(2);
	}

	/** Taking a claim back is a claim, and it is the one that clears the dates. */
	@Test
	void puttingWorkBackToNotStartedIsRecordedLikeAnythingElse() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.DONE, STARTED, FINISHED, null).andExpect(status().isOk());
		progress(this.ada, this.migration, WorkItemStatus.NOT_STARTED, null, null, null).andExpect(status().isOk());

		List<WorkItemProgress> history = this.reports.findForItem(this.acme.getId(), this.migration.getId());
		assertThat(history.get(0).getStatus()).isEqualTo(WorkItemStatus.NOT_STARTED);
		assertThat(history.get(0).getStartedOn()).isNull();
	}

	@Test
	void aRefusedClaimIsNotRecorded() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, null, null, null)
			.andExpect(status().isBadRequest());

		assertThat(this.reports.findForItem(this.acme.getId(), this.migration.getId())).isEmpty();
	}

	@Test
	void aClaimAboutAnotherOrganisationsWorkIsNotRecorded() throws Exception {
		progress(this.grace, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null)
			.andExpect(status().isNotFound());

		assertThat(this.reports.findAll()).isEmpty();
	}

	// The boundary M8 scores against -------------------------------------------

	/**
	 * <strong>The case this table was added for.</strong> A start moved forward would
	 * make an estimate written in between look like a forecast, so the boundary reads the
	 * first thing anybody said rather than the last.
	 */
	@Test
	void theBoundaryIsTheEarliestStartEverClaimedRatherThanTheCurrentOne() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, EARLIER, null, null).andExpect(status().isOk());
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());

		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getStartedOn()).isEqualTo(STARTED);
		assertThat(boundaries()).containsEntry(this.migration.getId(), EARLIER);
	}

	/** And a start moved *backwards* is taken as well, for the same reason. */
	@Test
	void theBoundaryTakesAStartThatWasCorrectedToAnEarlierDay() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, EARLIER, null, null).andExpect(status().isOk());

		assertThat(boundaries()).containsEntry(this.migration.getId(), EARLIER);
	}

	/**
	 * <strong>{@code V16} backfilled nothing, so this is the ordinary case for anything
	 * reported before it existed</strong>: the column is the only claim there has ever
	 * been, and dropping it would silently give every older item no boundary at all.
	 */
	@Test
	void anItemWithNoReportsFallsBackToTheDateOnTheRow() {
		this.migration.recordProgress(WorkItemStatus.IN_PROGRESS, EARLIER, null, null);
		this.items.save(this.migration);

		assertThat(boundaries()).containsEntry(this.migration.getId(), EARLIER);
	}

	/**
	 * A claim withdrawn from the row still happened. Reading only the column here would
	 * lose the boundary the moment somebody put work back to not started.
	 */
	@Test
	void aStartClearedFromTheRowIsStillTheBoundary() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());
		progress(this.ada, this.migration, WorkItemStatus.NOT_STARTED, null, null, null).andExpect(status().isOk());

		assertThat(this.items.findById(this.migration.getId()).orElseThrow().getStartedOn()).isNull();
		assertThat(boundaries()).containsEntry(this.migration.getId(), STARTED);
	}

	@Test
	void workNobodyHasClaimedAStartForHasNoBoundaryAtAll() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.DONE, null, FINISHED, null).andExpect(status().isOk());

		assertThat(boundaries()).doesNotContainKey(this.migration.getId());
	}

	@Test
	void boundariesCoverAWholeOrganisationAtOnce() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());
		progress(this.linus, this.rollout, WorkItemStatus.IN_PROGRESS, EARLIER, null, null).andExpect(status().isOk());

		assertThat(boundaries()).containsOnly(Map.entry(this.migration.getId(), STARTED),
				Map.entry(this.rollout.getId(), EARLIER));
	}

	@Test
	void boundariesFromAnotherOrganisationAreInvisible() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());

		assertThat(this.service.earliestReportedStarts(this.grace.getUser().getId(), this.grace.getTenant().getId()))
			.isEmpty();
	}

	@Test
	void boundariesAreRefusedToSomebodyWhoNoLongerBelongs() {
		UUID stranger = this.grace.getUser().getId();
		UUID tenantId = this.acme.getId();
		assertThatExceptionOfType(NotAMemberException.class)
			.isThrownBy(() -> this.service.earliestReportedStarts(stranger, tenantId));
	}

	// Reading it back ----------------------------------------------------------

	@Test
	void theHistoryOfOneItemIsReadableNewestFirst() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());
		progress(this.linus, this.migration, WorkItemStatus.DONE, STARTED, FINISHED, new BigDecimal("9.25"))
			.andExpect(status().isOk());

		history(this.ada, this.migration).andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].reportedByName").value("Linus"))
			.andExpect(jsonPath("$[0].status").value("DONE"))
			.andExpect(jsonPath("$[0].completedOn").value("2026-08-14"))
			.andExpect(jsonPath("$[0].actualEffortHours").value(9.25))
			.andExpect(jsonPath("$[0].reportedById").value(this.linus.getUser().getId().toString()))
			.andExpect(jsonPath("$[0].itemId").value(this.migration.getId().toString()))
			.andExpect(jsonPath("$[1].reportedByName").value("Ada"))
			.andExpect(jsonPath("$[1].status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$[1].completedOn").value(nullValue()));
	}

	@Test
	void workNobodyHasReportedOnHasAnEmptyHistoryRatherThanNone() throws Exception {
		history(this.ada, this.migration).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
	}

	/**
	 * "No such work" and "nobody has said anything about it" are different answers, and
	 * only one of them is worth acting on — the same reason listing the items in a plan
	 * that is not there is a refusal rather than an empty list.
	 */
	@Test
	void theHistoryOfWorkThatIsNotThereIsARefusal() throws Exception {
		this.mvc
			.perform(get("/api/items/" + UUID.randomUUID() + "/progress").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));
	}

	@Test
	void cannotReadTheHistoryOfWorkInAnotherOrganisation() throws Exception {
		progress(this.ada, this.migration, WorkItemStatus.IN_PROGRESS, STARTED, null, null).andExpect(status().isOk());

		history(this.grace, this.migration).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));
	}

	@Test
	void readingTheHistoryRequiresAToken() throws Exception {
		this.mvc.perform(get("/api/items/" + this.migration.getId() + "/progress"))
			.andExpect(status().isUnauthorized());
	}

	// Fixtures -----------------------------------------------------------------

	private Map<UUID, LocalDate> boundaries() {
		return this.service.earliestReportedStarts(this.ada.getUser().getId(), this.acme.getId());
	}

	private ResultActions progress(Membership caller, WorkItem item, WorkItemStatus status, LocalDate startedOn,
			LocalDate completedOn, BigDecimal actualEffortHours) throws Exception {
		UpdateProgressRequest request = new UpdateProgressRequest(status, startedOn, completedOn, actualEffortHours);
		return this.mvc
			.perform(patch("/api/items/" + item.getId() + "/progress").header(HttpHeaders.AUTHORIZATION, bearer(caller))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(request)));
	}

	private ResultActions history(Membership caller, WorkItem item) throws Exception {
		return this.mvc
			.perform(get("/api/items/" + item.getId() + "/progress").header(HttpHeaders.AUTHORIZATION, bearer(caller)));
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
