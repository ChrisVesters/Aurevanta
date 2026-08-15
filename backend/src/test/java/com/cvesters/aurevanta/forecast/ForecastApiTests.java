package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.dependency.Dependency;
import com.cvesters.aurevanta.dependency.DependencyRepository;
import com.cvesters.aurevanta.estimate.Estimate;
import com.cvesters.aurevanta.estimate.EstimateRepository;
import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.Schedule;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemRepository;
import com.cvesters.aurevanta.item.WorkItemStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asking a real plan a real question, and keeping the answer.
 *
 * <p>
 * <strong>{@code aStoredRunReplaysToTheNumbersItReported} is the one that
 * matters.</strong> It is the whole of what decision 9 promised: a run's snapshot, its
 * seed and its engine version, fed back through the engine, produce the percentiles
 * sitting in its own columns. If anything a forecast depended on were left out of the
 * snapshot, that test is the only thing in the suite that would notice — everything else
 * would go on passing while the history quietly became unreplayable, which is a thing
 * nobody discovers until M10 needs it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ForecastApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-14T09:00:00Z");

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
	private EstimateRepository estimates;

	@Autowired
	private DependencyRepository dependencies;

	@Autowired
	private ForecastRunRepository runs;

	@Autowired
	private ForecastService forecasts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership grace;

	private Project acmePlan;

	private Project umbrellaPlan;

	private WorkItem migration;

	private WorkItem rollout;

	/**
	 * Tenants go before users, and that order is load-bearing rather than tidy.
	 * {@code estimates.estimator_user_id} and {@code forecast_runs.requested_by_user_id}
	 * both point at a person and deliberately do not cascade — an estimator outliving
	 * their membership is the point — so a wipe that took the users out first would be
	 * refused by the database. Deleting the organisation cascades everything it owns and
	 * leaves the people behind to be removed on their own.
	 */
	@BeforeEach
	void seedAPlanWithEstimatedWorkInIt() {
		this.runs.deleteAll();
		this.dependencies.deleteAll();
		this.estimates.deleteAll();
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.MEMBER);
		this.acmePlan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
		this.umbrellaPlan = this.projects.save(new Project(umbrella, "Containment", null, CREATED_AT));
		this.migration = this.items.save(new WorkItem(this.acmePlan, "Migrate the auth service", null, CREATED_AT));
		this.rollout = this.items.save(new WorkItem(this.acmePlan, "Roll it out", null, CREATED_AT.plusSeconds(1)));
		estimate(this.migration, "8.00", "16.00", "40.00");
		estimate(this.rollout, "2.00", "4.00", "12.00");
	}

	// Asking for one ----------------------------------------------------------

	@Test
	void aMemberCanForecastAPlan() throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"capacity\":1}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.projectId").value(this.acmePlan.getId().toString()))
			.andExpect(jsonPath("$.requestedByName").value("Ada"))
			.andExpect(jsonPath("$.capacity").value(1))
			.andExpect(jsonPath("$.sampleCount").value(Engine.DEFAULT_SAMPLE_COUNT))
			.andExpect(jsonPath("$.engineVersion").value(Engine.VERSION))
			.andExpect(jsonPath("$.priorityRule").value(Schedule.PRIORITY_RULE))
			.andExpect(jsonPath("$.itemCount").value(2))
			.andExpect(jsonPath("$.estimatedItemCount").value(2))
			.andExpect(jsonPath("$.p90Hours").isNumber())
			.andExpect(jsonPath("$.histogram.counts.length()").value(100));
	}

	/**
	 * <strong>Decision 9, and the reason this table stores a seed at all.</strong>
	 * Nothing else here would notice a snapshot that had quietly stopped being complete.
	 */
	@Test
	void aStoredRunReplaysToTheNumbersItReported() throws Exception {
		ForecastRun run = forecast("{\"capacity\":2}");
		ForecastInputs inputs = this.forecasts.inputsOf(run);

		Forecast replayed = Engine.run(inputs.toModels(), inputs.toPrecedences(), run.getCapacity(),
				run.getSampleCount(), run.getSeed());

		// To the hundredth of an hour its columns keep, which is thirty-six seconds.
		assertThat(rounded(replayed.p10Hours())).isEqualByComparingTo(run.getP10Hours());
		assertThat(rounded(replayed.p50Hours())).isEqualByComparingTo(run.getP50Hours());
		assertThat(rounded(replayed.p80Hours())).isEqualByComparingTo(run.getP80Hours());
		assertThat(rounded(replayed.p90Hours())).isEqualByComparingTo(run.getP90Hours());
		assertThat(rounded(replayed.p95Hours())).isEqualByComparingTo(run.getP95Hours());
		assertThat(rounded(replayed.meanHours())).isEqualByComparingTo(run.getMeanHours());
	}

	@Test
	void theSnapshotHoldsWhatEverybodyActuallySaid() throws Exception {
		ForecastInputs inputs = this.forecasts.inputsOf(forecast("{\"capacity\":1}"));

		assertThat(inputs.items()).hasSize(2);
		assertThat(inputs.items().getFirst().id()).isEqualTo(this.migration.getId());
		assertThat(inputs.items().getFirst().estimates()).singleElement().satisfies((range) -> {
			assertThat(range.estimatorId()).isEqualTo(this.ada.getUser().getId());
			assertThat(range.p10Hours()).isEqualByComparingTo("8.00");
			assertThat(range.p50Hours()).isEqualByComparingTo("16.00");
			assertThat(range.p90Hours()).isEqualByComparingTo("40.00");
		});
	}

	@Test
	void moreCapacityForecastsAnEarlierFinish() throws Exception {
		ForecastRun alone = forecast("{\"capacity\":1}");
		ForecastRun together = forecast("{\"capacity\":2}");

		assertThat(together.getP90Hours()).isLessThan(alone.getP90Hours());
	}

	@Test
	void anArrowMakesThePlanTakeLonger() throws Exception {
		ForecastRun apart = forecast("{\"capacity\":2}");
		this.dependencies.save(new Dependency(this.migration, this.rollout, new BigDecimal("0.00"), CREATED_AT));

		ForecastRun inOrder = forecast("{\"capacity\":2}");

		assertThat(inOrder.getP90Hours()).isGreaterThan(apart.getP90Hours());
	}

	@Test
	void theCallerMaySayHowManyRunsToDo() throws Exception {
		assertThat(forecast("{\"capacity\":1,\"sampleCount\":2000}").getSampleCount()).isEqualTo(2000);
	}

	// Coverage and limitations ------------------------------------------------

	@Test
	void coverageOnTheRunMatchesWhatThePlanSaysAboutItself() throws Exception {
		this.items.save(new WorkItem(this.acmePlan, "Nobody has costed this", null, CREATED_AT.plusSeconds(2)));

		ForecastRun run = forecast("{\"capacity\":1}");

		assertThat(run.getItemCount()).isEqualTo(3);
		assertThat(run.getEstimatedItemCount()).isEqualTo(2);
		this.mvc
			.perform(get("/api/projects/" + this.acmePlan.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.itemCount").value(run.getItemCount()))
			.andExpect(jsonPath("$.estimatedItemCount").value(run.getEstimatedItemCount()));
	}

	/**
	 * <strong>Decision 12.</strong> The first two are always there, because they describe
	 * an engine that samples every item independently and forecasts only the work
	 * somebody wrote down — and a band reported without them is this product's own
	 * failure mode with a chart on it.
	 */
	@Test
	void everyForecastSaysWhatTheModelDidNotDo() throws Exception {
		ForecastRun run = forecast("{\"capacity\":1}");

		assertThat(this.forecasts.outputsOf(run).limitations())
			.containsExactlyInAnyOrder(ForecastLimitation.NO_TEAM_FACTOR, ForecastLimitation.NO_SCOPE_UNCERTAINTY);
	}

	@Test
	void aPlanWithWorkNobodyCostedSaysSo() throws Exception {
		this.items.save(new WorkItem(this.acmePlan, "Nobody has costed this", null, CREATED_AT.plusSeconds(2)));

		assertThat(this.forecasts.outputsOf(forecast("{\"capacity\":1}")).limitations())
			.contains(ForecastLimitation.UNESTIMATED_ITEMS);
	}

	@Test
	void aMiddleThatArguesWithItsOwnEndsSaysSo() throws Exception {
		WorkItem odd = this.items
			.save(new WorkItem(this.acmePlan, "Five to forty, middle at ten", null, CREATED_AT.plusSeconds(2)));
		estimate(odd, "5.00", "10.00", "40.00");

		assertThat(this.forecasts.outputsOf(forecast("{\"capacity\":1}")).limitations())
			.contains(ForecastLimitation.INCONSISTENT_ESTIMATES);
	}

	/**
	 * <strong>An arrow into work put away since is dropped, and said out loud.</strong>
	 * Archived work is never going to finish, so waiting for it would be waiting forever
	 * — but silently ignoring a constraint is how a plan comes out early for a reason
	 * nobody can see, which is the mistake M2's own progress endpoint had to be corrected
	 * for.
	 */
	@Test
	void anArrowIntoWorkPutAwaySinceIsDroppedAndReported() throws Exception {
		WorkItem shelved = this.items.save(new WorkItem(this.acmePlan, "Shelved", null, CREATED_AT.plusSeconds(2)));
		this.dependencies.save(new Dependency(shelved, this.rollout, new BigDecimal("0.00"), CREATED_AT));
		shelved.archive(CREATED_AT);
		this.items.save(shelved);

		ForecastRun run = forecast("{\"capacity\":2}");

		assertThat(this.forecasts.outputsOf(run).limitations())
			.contains(ForecastLimitation.DEPENDENCIES_ON_ARCHIVED_WORK);
		assertThat(this.forecasts.inputsOf(run).edges()).isEmpty();
		assertThat(run.getItemCount()).isEqualTo(2);
	}

	/** And the other way round: live work pointing at something put away since. */
	@Test
	void anArrowOutOfLiveWorkIntoWorkPutAwaySinceIsDroppedToo() throws Exception {
		WorkItem shelved = this.items.save(new WorkItem(this.acmePlan, "Shelved", null, CREATED_AT.plusSeconds(2)));
		this.dependencies.save(new Dependency(this.migration, shelved, new BigDecimal("0.00"), CREATED_AT));
		shelved.archive(CREATED_AT);
		this.items.save(shelved);

		ForecastRun run = forecast("{\"capacity\":2}");

		assertThat(this.forecasts.outputsOf(run).limitations())
			.contains(ForecastLimitation.DEPENDENCIES_ON_ARCHIVED_WORK);
		assertThat(this.forecasts.inputsOf(run).edges()).isEmpty();
	}

	/**
	 * <strong>Decision 5, reaching the stored snapshot.</strong> Effort already spent is
	 * what the conditional draw is conditioned on, so a snapshot that dropped it would
	 * replay to a different answer — and every other test here would go on passing. The
	 * plan also finishes sooner, because six hours of a task estimated at eight to forty
	 * have demonstrably already happened.
	 */
	@Test
	void workAlreadyUnderWayCarriesWhatItHasCostIntoTheSnapshot() throws Exception {
		ForecastRun fresh = forecast("{\"capacity\":1}");
		this.migration.recordProgress(WorkItemStatus.IN_PROGRESS, LocalDate.of(2026, 8, 12), null,
				new BigDecimal("6.00"));
		this.items.save(this.migration);

		ForecastRun started = forecast("{\"capacity\":1}");

		assertThat(this.forecasts.inputsOf(started).items().getFirst().spentHours()).isEqualByComparingTo("6.00");
		assertThat(this.forecasts.inputsOf(started).items().getFirst().status()).isEqualTo(WorkItemStatus.IN_PROGRESS);
		assertThat(started.getP90Hours()).isLessThan(fresh.getP90Hours());
	}

	// Refusals ----------------------------------------------------------------

	@Test
	void refusesAPlanNobodyHasEstimated() throws Exception {
		this.estimates.deleteAll();

		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"capacity\":1}"))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("nothing_to_forecast"));

		assertThat(this.runs.findAll()).isEmpty();
	}

	@Test
	void refusesAForecastWithNobodyToDoTheWork() throws Exception {
		refused("{\"capacity\":0}", "capacity", "positive");
		refused("{\"capacity\":-1}", "capacity", "positive");
	}

	@Test
	void refusesAForecastThatDoesNotSayHowManyPeopleThereAre() throws Exception {
		refused("{}", "capacity", "not_null");
	}

	@Test
	void refusesMoreRunsThanTheEngineWillDo() throws Exception {
		refused("{\"capacity\":1,\"sampleCount\":100001}", "sampleCount", "max");
		refused("{\"capacity\":1,\"sampleCount\":0}", "sampleCount", "positive");
	}

	@Test
	void cannotForecastAPlanInAnotherOrganisation() throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.umbrellaPlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"capacity\":1}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	/** Archiving says the plan is not being worked from, not that it is sealed. */
	@Test
	void anArchivedPlanStillForecasts() throws Exception {
		this.acmePlan.archive(CREATED_AT);
		this.projects.save(this.acmePlan);

		assertThat(forecast("{\"capacity\":1}").getP90Hours()).isPositive();
	}

	// Reading them back -------------------------------------------------------

	@Test
	void listsThePlansForecastsNewestFirst() throws Exception {
		ForecastRun first = forecast("{\"capacity\":1}");
		ForecastRun second = forecast("{\"capacity\":2}");

		this.mvc
			.perform(get("/api/projects/" + this.acmePlan.getId() + "/forecasts").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].id").value(second.getId().toString()))
			.andExpect(jsonPath("$[1].id").value(first.getId().toString()));
	}

	@Test
	void readsOneForecastByItsIdentifier() throws Exception {
		ForecastRun run = forecast("{\"capacity\":3}");

		this.mvc.perform(get("/api/forecasts/" + run.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(run.getId().toString()))
			.andExpect(jsonPath("$.capacity").value(3))
			// A string, because sixty-four bits do not survive a JSON number in a
			// browser. `isString` is what makes this an assertion at all: `value`
			// re-reads the document as the expected type, so it passes against a
			// number as happily as against the string this field has to be.
			.andExpect(jsonPath("$.seed").isString())
			.andExpect(jsonPath("$.seed").value(String.valueOf(run.getSeed())))
			.andExpect(jsonPath("$.limitations").isArray());
	}

	@Test
	void aForecastInAnotherOrganisationIsNotFound() throws Exception {
		ForecastRun run = forecast("{\"capacity\":1}");

		this.mvc.perform(get("/api/forecasts/" + run.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.grace)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("forecast_not_found"));
	}

	@Test
	void anIdentifierNothingHasIsTheSameAnswer() throws Exception {
		this.mvc.perform(get("/api/forecasts/" + UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("forecast_not_found"));
	}

	@Test
	void cannotListTheForecastsOfAnotherOrganisationsPlan() throws Exception {
		this.mvc
			.perform(get("/api/projects/" + this.umbrellaPlan.getId() + "/forecasts").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/forecasts"))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * The assertion the M2 review found missing from {@code EstimateApiTests}, written
	 * here rather than inherited: an identity token names a person and no organisation,
	 * and a forecast is tenant-owned data.
	 */
	@Test
	void requiresATokenScopedToAnOrganisation() throws Exception {
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + identity)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"capacity\":1}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void refusesSomebodyRemovedFromTheOrganisationSinceTheirTokenWasIssued() throws Exception {
		String stale = bearer(this.ada);
		this.memberships.delete(this.ada);

		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, stale)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"capacity\":1}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));

		assertThat(this.runs.findAll()).isEmpty();
	}

	// Fixtures ----------------------------------------------------------------

	private ForecastRun forecast(String body) throws Exception {
		String response = this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(this.json.readTree(response).get("id").asString());
		return this.runs.findById(id).orElseThrow();
	}

	private void refused(String body, String field, String code) throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors." + field + ".code").value(code));
	}

	private static BigDecimal rounded(double hours) {
		return BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
	}

	private Estimate estimate(WorkItem item, String p10, String p50, String p90) {
		return this.estimates.save(new Estimate(item, this.ada.getUser(), new BigDecimal(p10), new BigDecimal(p50),
				new BigDecimal(p90), CREATED_AT));
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
