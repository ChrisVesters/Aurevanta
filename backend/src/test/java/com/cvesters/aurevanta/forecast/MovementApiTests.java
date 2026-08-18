package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import org.springframework.test.web.servlet.ResultActions;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.estimate.Elicitation;
import com.cvesters.aurevanta.estimate.Estimate;
import com.cvesters.aurevanta.estimate.EstimateRepository;
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
 * Why the date moved between two forecasts of one plan.
 *
 * <p>
 * <strong>The oracle is that the terms add up.</strong> Every case here asserts it, at
 * every confidence, because it is the whole claim: an account of a movement that accounts
 * for most of the movement is not an account, and the difference between "most" and "all"
 * is invisible in any single reading. It holds by construction — each term is measured
 * with every earlier one applied, and the last state is the newer run itself — so a
 * failure here means the construction has been taken apart rather than that a number is
 * slightly off.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MovementApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-10T08:00:00Z");

	private static final LocalDate MONDAY = LocalDate.parse("2026-08-17");

	private static final String WORKING_DAY = "6.00";

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
	private ForecastRunRepository runs;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	@Autowired
	private JdbcTemplate database;

	private Membership ada;

	private Membership grace;

	private Project plan;

	private Project theirPlan;

	private Project secondPlan;

	private WorkItem migration;

	@BeforeEach
	void seedAPlanWithEstimatedWorkInIt() {
		this.runs.deleteAll();
		this.estimates.deleteAll();
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
		this.theirPlan = this.projects.save(new Project(umbrella, "Containment", null, CREATED_AT));
		this.secondPlan = this.projects.save(new Project(acme, "Q4 platform work", null, CREATED_AT));
		WorkItem next = this.items.save(new WorkItem(this.secondPlan, "Next quarter's work", null, CREATED_AT));
		estimate(next, "4.00", "8.00", "20.00");
		this.migration = item("Migrate the auth service", "8.00", "16.00", "40.00");
		item("Roll it out", "2.00", "4.00", "12.00");
	}

	// The oracle ---------------------------------------------------------------

	/**
	 * <strong>Nothing changed at all, so every term is nothing.</strong> The two runs
	 * differ only in their seeds — which is a difference, and is what the sampling term
	 * exists to absorb: the plan for this milestone did not name that step, and without
	 * it the terms sum to the distance between two things nobody was shown.
	 */
	@Test
	void twoRunsOfAnUnchangedPlanMoveOnlyBecauseTheyWereRunTwice() throws Exception {
		UUID first = forecast(assuming(2));
		UUID second = forecast(assuming(2));

		JsonNode at = readMovement(second, first, 80);

		assertThat(termsSum(at)).isEqualTo(totalMoved(at));
		for (String step : new String[] { "PROGRESS", "ESTIMATES", "SCOPE", "ASSUMPTIONS", "CALENDAR", "STARTS_ON" }) {
			assertThat(termOf(at, step)).as("%s of an unchanged plan", step).isZero();
		}
	}

	/**
	 * <strong>The claim, at every confidence the control offers.</strong> Somebody adds
	 * work, revises an estimate and reports progress between two forecasts, and the five
	 * days the date moved are five days accounted for.
	 */
	@Test
	void theTermsAddUpToTheWholeMovement() throws Exception {
		UUID before = forecast(assuming(2));
		this.migration.recordProgress(WorkItemStatus.IN_PROGRESS, MONDAY, null, new BigDecimal("6.00"));
		this.items.save(this.migration);
		estimate(this.migration, "20.00", "30.00", "60.00");
		item("Something nobody had listed", "10.00", "20.00", "50.00");
		UUID after = forecast(assuming(2));

		for (int confidence : new int[] { 50, 80, 95 }) {
			JsonNode at = readMovement(after, before, confidence);
			assertThat(termsSum(at)).as("at %d%%", confidence).isEqualTo(totalMoved(at));
		}
	}

	/** And it still holds when the question changed rather than the plan. */
	@Test
	void theTermsAddUpWhenTheAssumptionsChanged() throws Exception {
		UUID before = forecast(assuming(3));
		UUID after = forecast(assuming(1));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termsSum(at)).isEqualTo(totalMoved(at));
		assertThat(termOf(at, "ASSUMPTIONS")).isNotZero();
	}

	// Each term on its own ------------------------------------------------------

	/**
	 * A capacity that halved is not a plan that slipped, and the point of the account is
	 * that it says so on its own line rather than inside a total.
	 */
	@Test
	void changingTheCapacityIsAnAssumptionAndNothingElse() throws Exception {
		UUID before = forecast(assuming(3));
		UUID after = forecast(assuming(1));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "SCOPE")).isZero();
		assertThat(termOf(at, "ESTIMATES")).isZero();
		assertThat(termOf(at, "PROGRESS")).isZero();
		assertThat(termOf(at, "ASSUMPTIONS")).isNotZero();
	}

	@Test
	void addingWorkIsScopeAndNothingElse() throws Exception {
		UUID before = forecast(assuming(2));
		item("Something nobody had listed", "40.00", "60.00", "120.00");
		UUID after = forecast(assuming(2));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "SCOPE")).isPositive();
		assertThat(termOf(at, "ASSUMPTIONS")).isZero();
		assertThat(termOf(at, "ESTIMATES")).isZero();
	}

	@Test
	void revisingARangeIsEstimatesAndNothingElse() throws Exception {
		UUID before = forecast(assuming(2));
		estimate(this.migration, "40.00", "60.00", "120.00");
		UUID after = forecast(assuming(2));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "ESTIMATES")).isPositive();
		assertThat(termOf(at, "SCOPE")).isZero();
		assertThat(termOf(at, "PROGRESS")).isZero();
	}

	/**
	 * <strong>The one term that usually pulls the other way.</strong> Work finished
	 * between two forecasts is work the second one does not have to predict, so a plan
	 * that did nothing but deliver comes in earlier — and it is first in the order
	 * because it is not a decision anybody took, it is the baseline the rest is measured
	 * from.
	 */
	@Test
	void finishingWorkIsProgressAndBringsTheDateIn() throws Exception {
		UUID before = forecast(assuming(2));
		this.migration.recordProgress(WorkItemStatus.DONE, MONDAY, MONDAY.plusDays(2), new BigDecimal("12.00"));
		this.items.save(this.migration);
		UUID after = forecast(assuming(2));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "PROGRESS")).isNegative();
		assertThat(termOf(at, "SCOPE")).isZero();
		assertThat(termsSum(at)).isEqualTo(totalMoved(at));
	}

	/**
	 * A working day that changed moves the date and not one hour of the answer, which is
	 * why it is a step of its own — a reader who has just adjusted it should be told that
	 * is what they did rather than seeing it inside "the assumptions".
	 */
	@Test
	void changingTheWorkingDayIsACalendarTermAndMovesNoHours() throws Exception {
		UUID before = forecast(assuming(2, MONDAY, "6.00"));
		UUID after = forecast(assuming(2, MONDAY, "12.00"));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "CALENDAR")).isNegative();
		// It changes the divisor and not one hour of the answer, so every other term that
		// could have moved the hours is nothing — and the two ends differ only by the
		// seed.
		for (String step : new String[] { "PROGRESS", "ESTIMATES", "SCOPE", "ASSUMPTIONS", "STARTS_ON" }) {
			assertThat(termOf(at, step)).as("%s of a plan that only changed its working day", step).isZero();
		}
		assertThat(termsSum(at)).isEqualTo(totalMoved(at));
	}

	/** And a plan started later finishes later, for a reason nobody decided. */
	@Test
	void startingLaterIsTimePassing() throws Exception {
		UUID before = forecast(assuming(2, MONDAY, WORKING_DAY));
		UUID after = forecast(assuming(2, MONDAY.plusWeeks(2), WORKING_DAY));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "STARTS_ON")).isEqualTo(14);
		assertThat(termsSum(at)).isEqualTo(totalMoved(at));
	}

	/**
	 * A plan whose first forecast predates M4 and whose second does not: the terms before
	 * the calendar arrives have no days to report and the ones after it do. Reading the
	 * early ones through today's calendar is exactly what `V14` refused to do by
	 * backfilling.
	 */
	@Test
	void aCalendarThatArrivedBetweenTheTwoLeavesTheEarlierTermsInHours() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));
		madeBeforeThereWasACalendar(before);

		JsonNode at = readMovement(after, before, 80);

		assertThat(at.get("fromDate").isNull()).isTrue();
		// The newer end has a date and every *term* is still in hours, which is right
		// rather
		// than a gap: going from no start date at all to one is not a movement of days,
		// and a
		// term is the distance between two dates or it is nothing.
		assertThat(at.get("toDate").isNull()).isFalse();
		for (JsonNode term : at.get("terms")) {
			assertThat(term.get("movedDays").isNull()).as("days of %s", term.get("step").asString()).isTrue();
		}
	}

	/**
	 * The other direction, and the one that will happen later: the server versions ahead,
	 * so a run read under a calendar this code has never heard of reports hours rather
	 * than a date worked out under the wrong rule.
	 */
	@Test
	void aCalendarThisVersionCannotReadReportsHoursRatherThanAWrongDate() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));
		madeUnderACalendarWeCannotRead(before);

		JsonNode at = readMovement(after, before, 80);

		assertThat(at.get("fromDate").isNull()).isTrue();
		assertThat(termDays(at, "PROGRESS")).isNull();
	}

	/**
	 * And half a calendar is no calendar. Not a state anything writes — `V14` nulled all
	 * three together — and the guard is what stops one arriving as a division by nothing.
	 */
	@Test
	void halfACalendarIsNoCalendar() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));
		this.database.update("update forecast_runs set working_hours_per_day = null where id = ?", before);

		JsonNode at = readMovement(after, before, 80);

		assertThat(at.get("fromDate").isNull()).isTrue();
	}

	/**
	 * The mirror of the case above, and it is a guard rather than a scenario: nothing
	 * this product does makes a *newer* run without a calendar, since every run today
	 * requires one. The columns allow it, so the code has to answer rather than divide by
	 * nothing — which is the same reason `dateUnder` checks all three of them.
	 */
	@Test
	void aStateThatLosesItsCalendarIsAlsoNoDays() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));
		madeBeforeThereWasACalendar(after);

		JsonNode at = readMovement(after, before, 80);

		assertThat(at.get("fromDate").isNull()).isFalse();
		assertThat(at.get("toDate").isNull()).isTrue();
		assertThat(termDays(at, "CALENDAR")).isNull();
	}

	// What it says about itself -------------------------------------------------

	@Test
	void namesTheOrderItAttributedIn() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));

		read(this.ada, after, before).andExpect(status().isOk())
			.andExpect(jsonPath("$.rule").value(Movement.RULE))
			.andExpect(jsonPath("$.simulations").value(6))
			.andExpect(jsonPath("$.fromRunId").value(before.toString()))
			.andExpect(jsonPath("$.toRunId").value(after.toString()))
			.andExpect(jsonPath("$.at.length()").value(3));
	}

	/** Which of the two is older is a fact about the rows, not something to demand. */
	@Test
	void namingThemTheOtherWayRoundIsTheSameAccount() throws Exception {
		UUID before = forecast(assuming(3));
		UUID after = forecast(assuming(1));

		String forwards = body(read(this.ada, after, before));
		String backwards = body(read(this.ada, before, after));

		assertThat(backwards).isEqualTo(forwards);
	}

	// Refusals ------------------------------------------------------------------

	/**
	 * Two plans in one organisation, so both runs are reachable and the check is the
	 * thing that refuses — an organisation away and the lookup would have refused first,
	 * which is a different answer for a different reason.
	 */
	@Test
	void refusesTwoRunsOfDifferentPlans() throws Exception {
		UUID ours = forecast(assuming(2));
		UUID otherPlan = forecast(this.secondPlan, assuming(2));

		read(this.ada, ours, otherPlan).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("forecast_not_comparable"));
	}

	/**
	 * <strong>M6's argument rather than a fussy check.</strong> A comparison across a
	 * version bump is not a rougher comparison; it is an exact account of a movement that
	 * never happened, and it would look entirely reasonable. The row is aged by hand
	 * because that is what a version bump does to every run already stored.
	 */
	@Test
	void refusesTwoRunsMadeByDifferentEnginesEntirely() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));
		madeByAnOlderEngine(before);

		read(this.ada, after, before).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("forecast_not_comparable"));
	}

	/**
	 * Work that was put away between two runs is work the second one does not hold, and
	 * the account has to survive an item existing on one side only.
	 */
	@Test
	void workPutAwayBetweenTheTwoIsScope() throws Exception {
		UUID before = forecast(assuming(2));
		this.migration.archive(CREATED_AT);
		this.items.save(this.migration);
		UUID after = forecast(assuming(2));

		JsonNode at = readMovement(after, before, 80);

		assertThat(termOf(at, "SCOPE")).isNegative();
		assertThat(termsSum(at)).isEqualTo(totalMoved(at));
	}

	/**
	 * A run made before there was a calendar has hours and no date, so the account has
	 * hours and no days — which is `V14`'s refusal to backfill a claim nobody made,
	 * arriving here.
	 */
	@Test
	void aRunWithNoCalendarIsAccountedForInHoursAndNotInDays() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));
		madeBeforeThereWasACalendar(before);
		madeBeforeThereWasACalendar(after);

		JsonNode at = readMovement(after, before, 80);

		assertThat(at.get("fromDate").isNull()).isTrue();
		assertThat(at.get("toDate").isNull()).isTrue();
		assertThat(at.get("fromHours").asDouble()).isPositive();
		for (JsonNode term : at.get("terms")) {
			assertThat(term.get("movedDays").isNull()).as("days of %s", term.get("step").asString()).isTrue();
			assertThat(term.get("movedHours").isNumber()).isTrue();
		}
	}

	@Test
	void anotherOrganisationsRunIsNotThere() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));

		read(this.grace, after, before).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("forecast_not_found"));
	}

	@Test
	void requiresAToken() throws Exception {
		UUID before = forecast(assuming(2));
		UUID after = forecast(assuming(2));

		this.mvc.perform(get("/api/forecasts/" + after + "/movement").param("since", before.toString()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresARunToCompareWith() throws Exception {
		UUID after = forecast(assuming(2));

		this.mvc
			.perform(get("/api/forecasts/" + after + "/movement").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.since.code").value("not_null"));
	}

	// Fixtures ------------------------------------------------------------------

	private JsonNode readMovement(UUID newer, UUID older, int confidence) throws Exception {
		JsonNode account = this.json.readTree(body(read(this.ada, newer, older)));
		for (JsonNode at : account.get("at")) {
			if (at.get("confidence").asInt() == confidence) {
				return at;
			}
		}
		throw new AssertionError("no account at " + confidence + "%");
	}

	/** What the terms come to, which must be what the two runs are apart. */
	private static int termsSum(JsonNode at) {
		int total = 0;
		for (JsonNode term : at.get("terms")) {
			total += term.get("movedDays").asInt();
		}
		return total;
	}

	private static int totalMoved(JsonNode at) {
		return (int) ChronoUnit.DAYS.between(LocalDate.parse(at.get("fromDate").asString()),
				LocalDate.parse(at.get("toDate").asString()));
	}

	private static Integer termDays(JsonNode at, String step) {
		for (JsonNode term : at.get("terms")) {
			if (term.get("step").asString().equals(step)) {
				return term.get("movedDays").isNull() ? null : term.get("movedDays").asInt();
			}
		}
		throw new AssertionError("no term " + step);
	}

	private static int termOf(JsonNode at, String step) {
		for (JsonNode term : at.get("terms")) {
			if (term.get("step").asString().equals(step)) {
				return term.get("movedDays").asInt();
			}
		}
		throw new AssertionError("no term " + step);
	}

	private ResultActions read(Membership caller, UUID runId, UUID since) throws Exception {
		return this.mvc.perform(get("/api/forecasts/" + runId + "/movement").param("since", since.toString())
			.header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private static String body(ResultActions answered) throws Exception {
		return answered.andReturn().getResponse().getContentAsString();
	}

	private UUID forecast(String assuming) throws Exception {
		return forecast(this.plan, assuming);
	}

	/** What a version bump does to every run already stored, done to one of them. */
	private void madeByAnOlderEngine(UUID runId) {
		this.database.update("update forecast_runs set engine_version = 1 where id = ?", runId);
	}

	/**
	 * A calendar is required of every run made today, so the only way to have one without
	 * is to be older than M4 — which is what `V14` left, and what this reproduces.
	 */
	private void madeBeforeThereWasACalendar(UUID runId) {
		this.database.update("update forecast_runs set starts_on = null, working_hours_per_day = null,"
				+ " calendar_rule = null where id = ?", runId);
	}

	private UUID theirForecast() throws Exception {
		WorkItem theirs = this.items.save(new WorkItem(this.theirPlan, "Contain it", null, CREATED_AT));
		this.estimates.save(new Estimate(theirs, this.grace.getUser(), new BigDecimal("4.00"), new BigDecimal("8.00"),
				new BigDecimal("20.00"), Elicitation.SURPRISE_FRAMED, CREATED_AT));
		String response = this.mvc
			.perform(post("/api/projects/" + this.theirPlan.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.grace))
				.contentType(MediaType.APPLICATION_JSON)
				.content(assuming(2)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(this.json.readTree(response).get("id").asString());
	}

	private UUID forecast(Project project, String assuming) throws Exception {
		String response = this.mvc
			.perform(post("/api/projects/" + project.getId() + "/forecasts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content(assuming))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(this.json.readTree(response).get("id").asString());
	}

	private void madeUnderACalendarWeCannotRead(UUID runId) {
		this.database.update("update forecast_runs set calendar_rule = 'lunar_month' where id = ?", runId);
	}

	private static String assuming(int capacity) {
		return assuming(capacity, MONDAY, WORKING_DAY);
	}

	private static String assuming(int capacity, LocalDate startsOn, String workingDay) {
		return """
				{"capacity":%d,"teamFactorWorseByPercent":0,"scopeGrowthP10Percent":0,\
				"scopeGrowthP90Percent":0,"startsOn":"%s","workingHoursPerDay":%s}""".formatted(capacity, startsOn,
				workingDay);
	}

	private WorkItem item(String title, String p10, String p50, String p90) {
		WorkItem item = this.items.save(new WorkItem(this.plan, title, null, CREATED_AT));
		estimate(item, p10, p50, p90);
		return item;
	}

	private void estimate(WorkItem item, String p10, String p50, String p90) {
		this.estimates.save(new Estimate(item, this.ada.getUser(), new BigDecimal(p10), new BigDecimal(p50),
				new BigDecimal(p90), Elicitation.SURPRISE_FRAMED, Instant.now()));
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
