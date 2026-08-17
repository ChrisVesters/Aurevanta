package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.dependency.Dependency;
import com.cvesters.aurevanta.dependency.DependencyRepository;
import com.cvesters.aurevanta.estimate.Elicitation;
import com.cvesters.aurevanta.estimate.Estimate;
import com.cvesters.aurevanta.estimate.EstimateRepository;
import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.EstimateQuality;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.Schedule;
import com.cvesters.aurevanta.forecast.model.ScopeGrowth;
import com.cvesters.aurevanta.forecast.model.TeamFactor;
import com.cvesters.aurevanta.forecast.model.WorkingCalendar;
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

	/** A Monday, so that a hand count over the weekends is possible at all. */
	private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);

	/** One person's day, and deliberately not four people's. */
	private static final String WORKING_DAY = "6.00";

	/** The five the response derives, and the mean deliberately not among them. */
	private static final List<String> DATE_FIELDS = List.of("p10Date", "p50Date", "p80Date", "p90Date", "p95Date");

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

	/**
	 * The only place in this suite that writes SQL, and it earns it: the rows this
	 * milestone has to keep readable are the ones nothing can create any more, so they
	 * are made the way {@code V14} left them rather than through a second entity
	 * constructor that would invite somebody to write one on purpose.
	 */
	@Autowired
	private JdbcTemplate database;

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
				.content(assuming(1, 0, 0, 0)))
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
		ForecastRun run = forecast(assuming(2, 30, 20, 60));

		assertThat(replayOf(run)).satisfies((replayed) -> {
			// To the hundredth of an hour its columns keep, which is thirty-six seconds.
			assertThat(rounded(replayed.p10Hours())).isEqualByComparingTo(run.getP10Hours());
			assertThat(rounded(replayed.p50Hours())).isEqualByComparingTo(run.getP50Hours());
			assertThat(rounded(replayed.p80Hours())).isEqualByComparingTo(run.getP80Hours());
			assertThat(rounded(replayed.p90Hours())).isEqualByComparingTo(run.getP90Hours());
			assertThat(rounded(replayed.p95Hours())).isEqualByComparingTo(run.getP95Hours());
			assertThat(rounded(replayed.meanHours())).isEqualByComparingTo(run.getMeanHours());
		});
	}

	/**
	 * <strong>What the version 1 rows in this table become, and why the migration could
	 * backfill them with zeros and call it true.</strong> A run that assumed nothing
	 * reads back as two parameters that take no draw at all, so replaying it under
	 * version 2 is running version 1 — which is the whole of the promise
	 * {@code Engine.VERSION} makes. Every forecast made before this milestone is one of
	 * these.
	 */
	@Test
	void aRunThatAssumedNothingReplaysTheWayVersionOneDid() throws Exception {
		ForecastRun run = forecast(2);

		assertThat(TeamFactor.from(run.getTeamFactorWorseByPercent().doubleValue())).isEqualTo(TeamFactor.NONE);
		assertThat(ScopeGrowth.from(run.getScopeGrowthP10Percent().doubleValue(),
				run.getScopeGrowthP90Percent().doubleValue()))
			.isEqualTo(ScopeGrowth.NONE);
		assertThat(rounded(replayOf(run).p90Hours())).isEqualByComparingTo(run.getP90Hours());
	}

	/**
	 * A run replayed from what it stored about itself — the assumptions included, which
	 * is what makes them columns rather than prose. Reading them off the run rather than
	 * naming them here is the point: a replay that had to be told what to assume would
	 * only prove that the test remembered.
	 */
	private Forecast replayOf(ForecastRun run) {
		ForecastInputs inputs = this.forecasts.inputsOf(run);
		return Engine.run(inputs.toModels(), inputs.toPrecedences(), run.getCapacity(),
				TeamFactor.from(run.getTeamFactorWorseByPercent().doubleValue()), ScopeGrowth
					.from(run.getScopeGrowthP10Percent().doubleValue(), run.getScopeGrowthP90Percent().doubleValue()),
				run.getSampleCount(), run.getSeed());
	}

	@Test
	void theSnapshotHoldsWhatEverybodyActuallySaid() throws Exception {
		ForecastInputs inputs = this.forecasts.inputsOf(forecast(1));

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
		ForecastRun alone = forecast(1);
		ForecastRun together = forecast(2);

		assertThat(together.getP90Hours()).isLessThan(alone.getP90Hours());
	}

	@Test
	void anArrowMakesThePlanTakeLonger() throws Exception {
		ForecastRun apart = forecast(2);
		this.dependencies.save(new Dependency(this.migration, this.rollout, new BigDecimal("0.00"), CREATED_AT));

		ForecastRun inOrder = forecast(2);

		assertThat(inOrder.getP90Hours()).isGreaterThan(apart.getP90Hours());
	}

	@Test
	void theCallerMaySayHowManyRunsToDo() throws Exception {
		assertThat(forecast(assuming(1, 0, 0, 0, 2000)).getSampleCount()).isEqualTo(2000);
	}

	// Coverage and limitations ------------------------------------------------

	@Test
	void coverageOnTheRunMatchesWhatThePlanSaysAboutItself() throws Exception {
		this.items.save(new WorkItem(this.acmePlan, "Nobody has costed this", null, CREATED_AT.plusSeconds(2)));

		ForecastRun run = forecast(1);

		assertThat(run.getItemCount()).isEqualTo(3);
		assertThat(run.getEstimatedItemCount()).isEqualTo(2);
		this.mvc
			.perform(get("/api/projects/" + this.acmePlan.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.itemCount").value(run.getItemCount()))
			.andExpect(jsonPath("$.estimatedItemCount").value(run.getEstimatedItemCount()));
	}

	/**
	 * <strong>The concrete definition of M3b being finished.</strong> Every M3a forecast
	 * carried {@code no_team_factor} and {@code no_scope_uncertainty}, because they
	 * described an engine that sampled every item independently and forecast only the
	 * work somebody had written down. Both are now modelled, so a plan with nothing else
	 * wrong with it has nothing to report — the notices went away by having their cause
	 * removed rather than their wording.
	 */
	@Test
	void aForecastOfAWellDescribedPlanHasNothingToConfessTo() throws Exception {
		ForecastRun run = forecast(assuming(1, 30, 20, 60));

		assertThat(this.forecasts.outputsOf(run).limitations()).isEmpty();
	}

	/**
	 * And the two retired codes are gone whatever was assumed. A run made with both
	 * parameters at zero models the same thing M3a did, and still does not say so this
	 * way: what it assumed is on the run itself now, where a reader can see the number
	 * rather than a note about its absence.
	 */
	@Test
	void noForecastStillReportsTheTwoLimitationsM3bRemoved() throws Exception {
		this.items.save(new WorkItem(this.acmePlan, "Nobody has costed this", null, CREATED_AT.plusSeconds(2)));

		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(assuming(1, 0, 0, 0)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.limitations").value(hasItem("unestimated_items")))
			.andExpect(jsonPath("$.limitations").value(not(hasItem("no_team_factor"))))
			.andExpect(jsonPath("$.limitations").value(not(hasItem("no_scope_uncertainty"))));
	}

	@Test
	void aPlanWithWorkNobodyCostedSaysSo() throws Exception {
		this.items.save(new WorkItem(this.acmePlan, "Nobody has costed this", null, CREATED_AT.plusSeconds(2)));

		assertThat(this.forecasts.outputsOf(forecast(1)).limitations()).contains(ForecastLimitation.UNESTIMATED_ITEMS);
	}

	@Test
	void aMiddleThatArguesWithItsOwnEndsSaysSo() throws Exception {
		WorkItem odd = this.items
			.save(new WorkItem(this.acmePlan, "Five to forty, middle at ten", null, CREATED_AT.plusSeconds(2)));
		estimate(odd, "5.00", "10.00", "40.00");

		assertThat(this.forecasts.outputsOf(forecast(1)).limitations())
			.contains(ForecastLimitation.INCONSISTENT_ESTIMATES);
	}

	/**
	 * <strong>A forecast and the plan screen cannot come to different conclusions about
	 * one estimate.</strong> Both ask {@link EstimateQuality}, so this asserts the
	 * limitation against what that function says rather than against a number written out
	 * here — which is what stops the two drifting if the threshold ever moves. The two
	 * ranges are chosen either side of it and the test does not say which is which.
	 */
	@Test
	void thePlanReportsAnInconsistentEstimateExactlyWhenTheEstimateItselfDoes() throws Exception {
		for (String[] range : new String[][] { { "8.00", "16.00", "40.00" }, { "5.00", "10.00", "40.00" } }) {
			this.estimates.deleteAll();
			this.runs.deleteAll();
			estimate(this.migration, range[0], range[1], range[2]);
			boolean odd = EstimateQuality
				.of(Double.parseDouble(range[0]), Double.parseDouble(range[1]), Double.parseDouble(range[2]))
				.inconsistent();

			assertThat(this.forecasts.outputsOf(forecast(1))
				.limitations()
				.contains(ForecastLimitation.INCONSISTENT_ESTIMATES)).as(String.join("/", range)).isEqualTo(odd);
		}
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

		ForecastRun run = forecast(2);

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

		ForecastRun run = forecast(2);

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
		ForecastRun fresh = forecast(1);
		this.migration.recordProgress(WorkItemStatus.IN_PROGRESS, LocalDate.of(2026, 8, 12), null,
				new BigDecimal("6.00"));
		this.items.save(this.migration);

		ForecastRun started = forecast(1);

		assertThat(this.forecasts.inputsOf(started).items().getFirst().spentHours()).isEqualByComparingTo("6.00");
		assertThat(this.forecasts.inputsOf(started).items().getFirst().status()).isEqualTo(WorkItemStatus.IN_PROGRESS);
		assertThat(started.getP90Hours()).isLessThan(fresh.getP90Hours());
	}

	// The calendar ------------------------------------------------------------

	@Test
	void aRunStoresAndReportsTheCalendarItWasReadUnder() throws Exception {
		ForecastRun run = forecast(assuming(2, 30, 20, 60));

		assertThat(run.getStartsOn()).isEqualTo(MONDAY);
		assertThat(run.getWorkingHoursPerDay()).isEqualByComparingTo(WORKING_DAY);
		assertThat(run.getCalendarRule()).isEqualTo(WorkingCalendar.RULE);
		this.mvc.perform(get("/api/forecasts/" + run.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.startsOn").value("2026-08-17"))
			.andExpect(jsonPath("$.workingHoursPerDay").value(6.00))
			.andExpect(jsonPath("$.calendarRule").value("five_day_week"));
	}

	/**
	 * <strong>An hour a day, so that the answer has the resolution to be tested.</strong>
	 * A band narrower than a working day lands on one date at every confidence, which is
	 * the calendar being honest rather than the control being broken — and is also a test
	 * that would pass against a response returning the same date five times.
	 */
	@Test
	void theFiveDatesAscendWithTheirPercentiles() throws Exception {
		JsonNode answer = reported(forecast(calendaring(MONDAY.toString(), "1.00")));
		List<LocalDate> dates = DATE_FIELDS.stream().map((field) -> dateAt(answer, field)).toList();

		assertThat(dates).isSorted();
		assertThat(dates.getFirst()).isBefore(dates.getLast());
		// The hours the model produced stay beside the dates it was read into: removing
		// them would leave nothing on the response that came out of the engine.
		assertThat(answer.get("p95Hours").decimalValue()).isGreaterThan(answer.get("p10Hours").decimalValue());
	}

	/**
	 * <strong>Each date is the one its own percentile produced, which nothing else here
	 * checks.</strong> The response is built from thirty-one positional arguments, and a
	 * date paired with the wrong column would still ascend, still differ from its
	 * neighbours and still pass every other case in this class — the ordering assertion
	 * above cannot see a p10 date derived from the p50 hours. So the pairing is asserted
	 * directly, against the hours sitting on the run.
	 */
	@Test
	void everyDateIsTheOneItsOwnPercentileProduces() throws Exception {
		ForecastRun run = forecast(calendaring(MONDAY.toString(), "1.00"));
		BigDecimal day = run.getWorkingHoursPerDay();
		List<BigDecimal> hours = List.of(run.getP10Hours(), run.getP50Hours(), run.getP80Hours(), run.getP90Hours(),
				run.getP95Hours());

		JsonNode answer = reported(run);

		for (int at = 0; at < DATE_FIELDS.size(); at++) {
			assertThat(dateAt(answer, DATE_FIELDS.get(at))).as(DATE_FIELDS.get(at))
				.isEqualTo(WorkingCalendar.finishOn(run.getStartsOn(), hours.get(at), day));
		}
	}

	/**
	 * <strong>Decision 4, and the reason the start is an input.</strong> A plan forecast
	 * today for a January start is forecast from January; a server that stamped its own
	 * clock here would answer a question nobody asked, in its own timezone.
	 */
	@Test
	void aStartDateTheServerWouldNotHaveChosenComesBackUnchanged() throws Exception {
		LocalDate january = LocalDate.of(2027, 1, 4);

		ForecastRun run = forecast(calendaring(january.toString(), WORKING_DAY));

		assertThat(run.getStartsOn()).isEqualTo(january);
		JsonNode answer = reported(run);
		assertThat(answer.get("startsOn").asString()).isEqualTo("2027-01-04");
		assertThat(dateAt(answer, "p10Date")).isAfterOrEqualTo(january);
	}

	/**
	 * <strong>Decision 6, asserted against the only kind of row that can show
	 * it.</strong> Nothing can create one through the API any more, so the columns are
	 * cleared directly — which is exactly what {@code V14} left behind, since it
	 * deliberately backfilled nothing. A run made last week did not assume a six-hour
	 * day; it assumed no calendar at all, and it says so rather than being handed one
	 * after the fact.
	 */
	@Test
	void aRunMadeBeforeThereWasACalendarReportsHoursAndNoDates() throws Exception {
		ForecastRun run = forecast(1);
		this.database.update("update forecast_runs set starts_on = null, working_hours_per_day = null,"
				+ " calendar_rule = null where id = ?", run.getId());

		JsonNode answer = reported(run);

		assertThat(answer.get("p90Hours").isNumber()).isTrue();
		assertThat(answer.get("startsOn").isNull()).isTrue();
		assertThat(answer.get("workingHoursPerDay").isNull()).isTrue();
		assertThat(answer.get("calendarRule").isNull()).isTrue();
		for (String field : DATE_FIELDS) {
			assertThat(answer.get(field).isNull()).as(field).isTrue();
		}
	}

	/**
	 * <strong>The rule on the response is the one the run stored, not the one this code
	 * happens to have.</strong> Writing {@code WorkingCalendar.RULE} into the response
	 * would make every historical run claim today's calendar, and reading its dates
	 * through today's calendar would make a rule change indistinguishable from a plan
	 * that moved — which is the whole reason the rule is a stored name rather than a
	 * boolean.
	 *
	 * <p>
	 * Only one rule exists, so this row cannot be made through the API. It is the shape
	 * M11 arrives in, and the day it does is the day the alternative starts lying
	 * quietly.
	 */
	@Test
	void aRunResolvesUnderTheCalendarItWasMadeWithAndNotTodays() throws Exception {
		ForecastRun run = forecast(1);
		this.database.update("update forecast_runs set calendar_rule = 'four_day_week' where id = ?", run.getId());

		JsonNode answer = reported(run);

		assertThat(answer.get("calendarRule").asString()).isEqualTo("four_day_week");
		assertThat(answer.get("startsOn").asString()).isEqualTo(MONDAY.toString());
		assertThat(answer.get("p90Hours").isNumber()).isTrue();
		for (String field : DATE_FIELDS) {
			assertThat(answer.get(field).isNull()).as(field).isTrue();
		}
	}

	// Refusals ----------------------------------------------------------------

	@Test
	void refusesAForecastThatDoesNotSayWhenWorkStartsOrWhatADayHolds() throws Exception {
		refused(calendaring(null, WORKING_DAY), "startsOn", "not_null");
		refused(calendaring(MONDAY.toString(), null), "workingHoursPerDay", "not_null");
	}

	/**
	 * Each against its own box, because each is a fact about one number somebody typed —
	 * unlike the growth range, which is a fact about a pair and so names neither.
	 */
	@Test
	void refusesAWorkingDayNobodyCouldWork() throws Exception {
		refused(calendaring(MONDAY.toString(), "0"), "workingHoursPerDay", "positive");
		refused(calendaring(MONDAY.toString(), "-6"), "workingHoursPerDay", "positive");
		refused(calendaring(MONDAY.toString(), "25"), "workingHoursPerDay", "max");
	}

	@Test
	void refusesAPlanNobodyHasEstimated() throws Exception {
		this.estimates.deleteAll();

		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(assuming(1, 0, 0, 0)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("nothing_to_forecast"));

		assertThat(this.runs.findAll()).isEmpty();
	}

	/**
	 * <strong>Three different numbers on purpose.</strong> Two of these columns would
	 * look perfectly well stored if the service handed them over in the wrong order, and
	 * nothing else in the suite would notice — so no two of them are the same here, in
	 * the request, on the row, or in the response.
	 */
	@Test
	void aRunStoresAndReportsTheAssumptionsItWasMadeUnder() throws Exception {
		ForecastRun run = forecast(assuming(2, 30, 20, 60));

		assertThat(run.getTeamFactorWorseByPercent()).isEqualByComparingTo("30.00");
		assertThat(run.getScopeGrowthP10Percent()).isEqualByComparingTo("20.00");
		assertThat(run.getScopeGrowthP90Percent()).isEqualByComparingTo("60.00");
		this.mvc.perform(get("/api/forecasts/" + run.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.teamFactorWorseByPercent").value(30.00))
			.andExpect(jsonPath("$.scopeGrowthP10Percent").value(20.00))
			.andExpect(jsonPath("$.scopeGrowthP90Percent").value(60.00));
	}

	/**
	 * <strong>A run made with zeros still says so.</strong> Zero is a claim — that
	 * nothing in this team's world has a common cause and that no unlisted work will
	 * appear — and a stored claim reads back the same as any other.
	 */
	@Test
	void aRunThatAssumedNothingSaysThatToo() throws Exception {
		ForecastRun run = forecast(1);

		assertThat(run.getTeamFactorWorseByPercent()).isEqualByComparingTo("0.00");
		assertThat(run.getScopeGrowthP10Percent()).isEqualByComparingTo("0.00");
		assertThat(run.getScopeGrowthP90Percent()).isEqualByComparingTo("0.00");
	}

	/** Both effects on makes a wider band than the same plan with neither. */
	@Test
	void assumingACommonCauseAndUnlistedWorkWidensTheBand() throws Exception {
		ForecastRun listed = forecast(2);

		ForecastRun honest = forecast(assuming(2, 30, 20, 60));

		assertThat(honest.getP90Hours()).isGreaterThan(listed.getP90Hours());
		assertThat(honest.getP90Hours().subtract(honest.getP10Hours()))
			.isGreaterThan(listed.getP90Hours().subtract(listed.getP10Hours()));
	}

	@Test
	void refusesAForecastThatDoesNotSayWhatItAssumes() throws Exception {
		refused("{\"capacity\":1}", "teamFactorWorseByPercent", "not_null");
		refused("{\"capacity\":1}", "scopeGrowthP10Percent", "not_null");
		refused("{\"capacity\":1}", "scopeGrowthP90Percent", "not_null");
	}

	@Test
	void refusesAnAssumptionThatCannotBeOne() throws Exception {
		refused(assuming(1, -1, 0, 0), "teamFactorWorseByPercent", "positive_or_zero");
		refused(assuming(1, 0, -1, 0), "scopeGrowthP10Percent", "positive_or_zero");
		refused(assuming(1, 0, 0, -1), "scopeGrowthP90Percent", "positive_or_zero");
	}

	/**
	 * The ceiling is where a forecast stops fitting inside the request that asked for it
	 * — measured, not picked — and past it is a number nobody could act on anyway.
	 */
	@Test
	void refusesAnAssumptionBeyondWhatIsWorthAsking() throws Exception {
		refused(assuming(1, 201, 0, 0), "teamFactorWorseByPercent", "max");
		refused(assuming(1, 0, 0, 201), "scopeGrowthP90Percent", "max");
	}

	/**
	 * <strong>A fact about the pair, so it names neither box.</strong> Both numbers are
	 * perfectly good percentages and what is wrong is the relationship between them,
	 * which is exactly why {@code estimate_out_of_order} exists rather than a field error
	 * — and why nothing here is swapped into the order the server would have preferred.
	 */
	@Test
	void refusesAGrowthRangeTheWrongWayRound() throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(assuming(1, 0, 60, 20)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("scope_growth_out_of_order"))
			.andExpect(jsonPath("$.errors").doesNotExist());

		assertThat(this.runs.findAll()).isEmpty();
	}

	/** A range of the same number twice is a certainty, not a mistake. */
	@Test
	void acceptsAGrowthRangeWithBothEndsTheSame() throws Exception {
		assertThat(forecast(assuming(1, 0, 25, 25)).getScopeGrowthP90Percent()).isEqualByComparingTo("25.00");
	}

	@Test
	void refusesAForecastWithNobodyToDoTheWork() throws Exception {
		refused(assuming(0, 0, 0, 0), "capacity", "positive");
		refused(assuming(-1, 0, 0, 0), "capacity", "positive");
	}

	@Test
	void refusesAForecastThatDoesNotSayHowManyPeopleThereAre() throws Exception {
		refused("{}", "capacity", "not_null");
	}

	@Test
	void refusesMoreRunsThanTheEngineWillDo() throws Exception {
		refused(assuming(1, 0, 0, 0, 100_001), "sampleCount", "max");
		refused(assuming(1, 0, 0, 0, 0), "sampleCount", "positive");
	}

	@Test
	void cannotForecastAPlanInAnotherOrganisation() throws Exception {
		this.mvc
			.perform(post("/api/projects/" + this.umbrellaPlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(assuming(1, 0, 0, 0)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	/** Archiving says the plan is not being worked from, not that it is sealed. */
	@Test
	void anArchivedPlanStillForecasts() throws Exception {
		this.acmePlan.archive(CREATED_AT);
		this.projects.save(this.acmePlan);

		assertThat(forecast(1).getP90Hours()).isPositive();
	}

	// Reading them back -------------------------------------------------------

	@Test
	void listsThePlansForecastsNewestFirst() throws Exception {
		ForecastRun first = forecast(1);
		ForecastRun second = forecast(2);

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
		ForecastRun run = forecast(3);

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
		ForecastRun run = forecast(1);

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
				.content(assuming(1, 0, 0, 0)))
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
				.content(assuming(1, 0, 0, 0)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));

		assertThat(this.runs.findAll()).isEmpty();
	}

	// Fixtures ----------------------------------------------------------------

	/**
	 * A forecast that assumes nothing beyond its capacity — no common cause and no
	 * growth, which is what the engine did before M3b and is now something somebody has
	 * to say.
	 */
	private ForecastRun forecast(int capacity) throws Exception {
		return forecast(assuming(capacity, 0, 0, 0));
	}

	/**
	 * The body of a request for a forecast. Three of a run's five assumptions became
	 * required in M3b, so there is no shorter honest way to ask for one.
	 */
	private static String assuming(int capacity, Number worseByPercent, Number growthP10, Number growthP90) {
		return assuming(capacity, worseByPercent, growthP10, growthP90, null);
	}

	/**
	 * The same, for the one field a caller may leave out — {@code null} asks for the
	 * ordinary ten thousand runs by saying nothing, which is a different request from one
	 * naming a number and has to be sent as one.
	 */
	private static String assuming(int capacity, Number worseByPercent, Number growthP10, Number growthP90,
			Integer sampleCount) {
		String runs = (sampleCount != null) ? ",\"sampleCount\":" + sampleCount : "";
		return """
				{"capacity":%d%s,"teamFactorWorseByPercent":%s,\
				"scopeGrowthP10Percent":%s,"scopeGrowthP90Percent":%s,\
				"startsOn":"%s","workingHoursPerDay":%s}""".formatted(capacity, runs, worseByPercent, growthP10,
				growthP90, MONDAY, WORKING_DAY);
	}

	/**
	 * A request naming a calendar of its own, for the cases where the calendar is what is
	 * under test. A {@code null} leaves that box out of the document altogether, which is
	 * the only honest way to send a required field somebody did not answer.
	 */
	private static String calendaring(String startsOn, String workingHoursPerDay) {
		String start = (startsOn != null) ? ",\"startsOn\":\"" + startsOn + "\"" : "";
		String day = (workingHoursPerDay != null) ? ",\"workingHoursPerDay\":" + workingHoursPerDay : "";
		return """
				{"capacity":1,"teamFactorWorseByPercent":0,\
				"scopeGrowthP10Percent":0,"scopeGrowthP90Percent":0%s%s}""".formatted(start, day);
	}

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

	/** One run as the API describes it, read back the way a client would. */
	private JsonNode reported(ForecastRun run) throws Exception {
		return this.json.readTree(this.mvc
			.perform(get("/api/forecasts/" + run.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	/**
	 * Parsed from the wire rather than compared as text, so that a field arriving as
	 * anything but an ISO date fails here instead of passing as an equal string.
	 */
	private static LocalDate dateAt(JsonNode answer, String field) {
		return LocalDate.parse(answer.get(field).asString());
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
				new BigDecimal(p90), Elicitation.THREE_POINT, CREATED_AT));
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
