package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.ResultActions;

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
import com.cvesters.aurevanta.requirement.Requirement;
import com.cvesters.aurevanta.requirement.RequirementRepository;
import com.cvesters.aurevanta.resource.Resource;
import com.cvesters.aurevanta.resource.ResourceRepository;
import com.cvesters.aurevanta.security.AccessTokenService;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.greaterThan;
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
 * nobody discovers until the reporting layer needs it.
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
	private ResourceRepository resources;

	@Autowired
	private RequirementRepository requirements;

	@Autowired
	private ForecastService forecasts;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	/**
	 * The only place in this suite that writes SQL, and it earns it: the rows this work
	 * has to keep readable are the ones nothing can create any more, so they are made the
	 * way {@code V14} left them rather than through a second entity constructor that
	 * would invite somebody to write one on purpose.
	 */
	@Autowired
	private JdbcTemplate database;

	private Membership ada;

	private Membership grace;

	private Project acmePlan;

	private Project umbrellaPlan;

	/** How many pools this case has declared, so each is a second after the last. */
	private int declared;

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
		this.declared = 0;
		this.runs.deleteAll();
		this.requirements.deleteAll();
		this.resources.deleteAll();
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
	 * {@code Engine.VERSION} makes. Every forecast made before this work is one of these.
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
	 * <strong>The concrete definition of the common-cause model being finished.</strong>
	 * Every the simulation engine forecast carried {@code no_team_factor} and
	 * {@code no_scope_uncertainty}, because they described an engine that sampled every
	 * item independently and forecast only the work somebody had written down. Both are
	 * now modelled, so a plan with nothing else wrong with it has nothing to report — the
	 * notices went away by having their cause removed rather than their wording.
	 */
	@Test
	void aForecastOfAWellDescribedPlanHasNothingToConfessTo() throws Exception {
		ForecastRun run = forecast(assuming(1, 30, 20, 60));

		assertThat(this.forecasts.outputsOf(run).limitations()).isEmpty();
	}

	/**
	 * And the two retired codes are gone whatever was assumed. A run made with both
	 * parameters at zero models the same thing the simulation engine did, and still does
	 * not say so this way: what it assumed is on the run itself now, where a reader can
	 * see the number rather than a note about its absence.
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
	 * nobody can see, which is the mistake the plan schema's own progress endpoint had to
	 * be corrected for.
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

	// What the spread is made of ----------------------------------------------

	/**
	 * <strong>Every forecast this product has ever made can be explained, because nothing
	 * was stored to explain it.</strong> The ranking comes from replaying the run out of
	 * its own seed — five million per-item durations that never had to be written down —
	 * which is what the simulation engine kept a seed for and the first feature to spend
	 * it.
	 */
	@Test
	void aStoredRunSaysWhatItsSpreadWasMadeOf() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode ranked = parsed(
				contributions(run).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

		assertThat(ranked.size()).isEqualTo(2);
		assertThat(itemIdsIn(ranked)).containsExactlyInAnyOrder(this.migration.getId().toString(),
				this.rollout.getId().toString());
		// A chain of two at capacity one is a sum, so the shares are the variance shares
		// and
		// add to one — the closed form `ContributionsTests` proves, arriving over HTTP.
		double total = 0.0;
		for (JsonNode source : ranked) {
			assertThat(source.get("kind").asString()).isEqualTo("item");
			total += source.get("shareOfSpread").asDouble();
		}
		assertThat(total).isCloseTo(1.0, within(0.05));
	}

	/**
	 * <strong>Titles come off the plan as it stands, because the snapshot never held
	 * one.</strong> That is deliberate — the reporting layer diffs those snapshots and a
	 * rename is not a thing that moved — and it is the right answer anyway: somebody
	 * reading a ranking is being told what to go and do, so the name the work has now is
	 * the useful one.
	 */
	@Test
	void aRankingNamesTheWorkAsThePlanNamesItNow() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		this.migration.describe("Migrate the auth service, at last", null);
		this.items.save(this.migration);

		JsonNode ranked = parsed(contributions(run).andReturn().getResponse().getContentAsString());

		assertThat(titlesIn(ranked)).contains("Migrate the auth service, at last");
	}

	/**
	 * And work put away since is named as such rather than shown as a blank — the same
	 * choice an arrow pointing at archived work already makes. A top contributor missing
	 * from the live plan is exactly what a reader would otherwise spend a minute hunting
	 * for.
	 */
	@Test
	void workPutAwaySinceTheRunIsStillNamedAndMarked() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		this.rollout.archive(CREATED_AT);
		this.items.save(this.rollout);

		JsonNode ranked = parsed(contributions(run).andReturn().getResponse().getContentAsString());

		JsonNode shelved = null;
		for (JsonNode source : ranked) {
			if (this.rollout.getId().toString().equals(source.get("itemId").asString())) {
				shelved = source;
			}
		}
		assertThat(shelved).isNotNull();
		assertThat(shelved.get("title").asString()).isEqualTo("Roll it out");
		assertThat(shelved.get("archived").asBoolean()).isTrue();
	}

	/**
	 * <strong>Work the plan no longer holds at all names itself as such.</strong> Nothing
	 * in this product deletes an item — they archive — so this is the shape of a bug
	 * rather than an ordinary state, and it is reachable here only by removing the row
	 * directly. The guard is worth keeping anyway: the alternative is a ranking that
	 * fails outright on a row somebody removed by hand, and a blank line is the one
	 * answer that helps nobody.
	 */
	@Test
	void workThePlanNoLongerHoldsIsRankedWithoutAName() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		this.database.update("delete from work_items where id = ?", this.rollout.getId());

		JsonNode ranked = parsed(contributions(run).andReturn().getResponse().getContentAsString());

		JsonNode gone = null;
		for (JsonNode source : ranked) {
			if (this.rollout.getId().toString().equals(source.get("itemId").asString())) {
				gone = source;
			}
		}
		assertThat(gone).isNotNull();
		assertThat(gone.get("title").isNull()).isTrue();
		assertThat(gone.get("archived").asBoolean()).isFalse();
	}

	/**
	 * Largest first, because the order is the feature rather than a client's to choose.
	 */
	@Test
	void theRankingComesBackRanked() throws Exception {
		JsonNode ranked = parsed(
				contributions(forecast(assuming(2, 30, 20, 60))).andReturn().getResponse().getContentAsString());

		double previous = Double.MAX_VALUE;
		for (JsonNode source : ranked) {
			double share = source.get("shareOfSpread").asDouble();
			assertThat(share).isLessThanOrEqualTo(previous);
			previous = share;
		}
	}

	/**
	 * <strong>The two rows that are not items, and the reason the ranking is
	 * honest.</strong> A list of tasks alone answers "which of these should I spike"
	 * while hiding whether spiking any of them is worth doing.
	 */
	@Test
	void theSharedFactorAndTheUnlistedWorkAreRowsOfTheirOwn() throws Exception {
		JsonNode ranked = parsed(
				contributions(forecast(assuming(2, 30, 20, 60))).andReturn().getResponse().getContentAsString());

		assertThat(kindsIn(ranked)).contains("team_factor", "discovered_work");
		for (JsonNode source : ranked) {
			if (!"item".equals(source.get("kind").asString())) {
				assertThat(source.get("itemId").isNull()).as("%s names no item", source.get("kind")).isTrue();
			}
		}
	}

	/**
	 * <strong>Absent rather than zero, and the difference is a claim.</strong> A source
	 * nobody modelled never varied, so it measures as nothing either way — but a row
	 * reading zero would invite a reader to conclude their team has no common cause, when
	 * what they did was decline to model one. Decided from what the run stored, which is
	 * the rule a forecast made before there was a calendar already follows.
	 */
	@Test
	void aRunThatAssumedNeitherGetsNeitherRow() throws Exception {
		JsonNode ranked = parsed(
				contributions(forecast(assuming(1, 0, 0, 0))).andReturn().getResponse().getContentAsString());

		assertThat(kindsIn(ranked)).containsOnly("item");
	}

	/**
	 * <strong>The guard this work turns on.</strong> A run keeps the six figures it
	 * produced, so replaying it and comparing them answers the only question that matters
	 * — does this still come out the same? A ranking from a different model is not a
	 * rougher ranking of this plan, it is an exact ranking of a plan nobody forecast, and
	 * it would look entirely reasonable. Altering a stored figure directly is the only
	 * way to reach this today; a version bump is what reaches it later.
	 */
	@Test
	void refusesToExplainARunItNoLongerReproduces() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		this.database.update("update forecast_runs set p90_hours = p90_hours + 1 where id = ?", run.getId());

		contributions(run).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("forecast_replay_mismatch"))
			.andExpect(jsonPath("$[0]").doesNotExist());
	}

	/**
	 * <strong>All six, and each one on its own.</strong> A change that moved only the
	 * mean, or only one tail, is exactly the change nobody would think to look for — so
	 * every figure a run stores is compared, and this walks them one at a time to say
	 * that none of them is being taken on trust.
	 */
	@Test
	void everyFigureARunStoresIsPartOfTheGuard() throws Exception {
		for (String figure : List.of("mean_hours", "p10_hours", "p50_hours", "p80_hours", "p90_hours", "p95_hours")) {
			this.runs.deleteAll();
			ForecastRun run = forecast(assuming(1, 0, 0, 0));
			contributions(run).andExpect(status().isOk());

			this.database.update("update forecast_runs set " + figure + " = " + figure + " + 1 where id = ?",
					run.getId());

			contributions(run).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("forecast_replay_mismatch"));
		}
	}

	/**
	 * A read is a read: explaining a run leaves it exactly as it was, and adds nothing.
	 */
	@Test
	void explainingARunWritesNothing() throws Exception {
		ForecastRun run = forecast(assuming(2, 30, 20, 60));

		contributions(run).andExpect(status().isOk());

		assertThat(this.runs.findAll()).singleElement().satisfies((unchanged) -> {
			assertThat(unchanged.getP90Hours()).isEqualByComparingTo(run.getP90Hours());
			assertThat(unchanged.getSeed()).isEqualTo(run.getSeed());
		});
	}

	@Test
	void cannotExplainAForecastInAnotherOrganisation() throws Exception {
		ForecastRun run = forecast(1);

		this.mvc
			.perform(get("/api/forecasts/" + run.getId() + "/contributions").header(HttpHeaders.AUTHORIZATION,
					bearer(this.grace)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("forecast_not_found"));
	}

	@Test
	void explainingARunNeedsATokenScopedToAnOrganisation() throws Exception {
		ForecastRun run = forecast(1);
		String identity = this.accessTokens.issueIdentityToken(this.ada.getUser()).value();

		this.mvc.perform(get("/api/forecasts/" + run.getId() + "/contributions").header(HttpHeaders.AUTHORIZATION,
				"Bearer " + identity))
			.andExpect(status().isForbidden());
	}

	// What it would take to hit a date ----------------------------------------

	/**
	 * <strong>Cutting what was never on the deciding path buys nothing, and cutting what
	 * was buys a great deal.</strong> That is the whole reason a candidate is simulated
	 * rather than ranked by size — no arithmetic over one item's estimate can say which
	 * case it is in, because the answer depends on what else the plan is doing at the
	 * time.
	 */
	@Test
	void whatACutBuysDependsOnWhetherItWasHoldingThePlanUp() throws Exception {
		WorkItem aside = this.items
			.save(new WorkItem(this.acmePlan, "Something small on its own", null, CREATED_AT.plusSeconds(2)));
		estimate(aside, "1.00", "2.00", "3.00");
		this.dependencies.save(new Dependency(this.migration, this.rollout, new BigDecimal("0.00"), CREATED_AT));
		ForecastRun run = forecast(assuming(2, 0, 0, 0));

		JsonNode answer = parsed(cuts(run, MONDAY.plusDays(4), 80, this.migration.getId(), aside.getId()).andReturn()
			.getResponse()
			.getContentAsString());

		assertThat(buysFor(answer, this.migration.getId())).isGreaterThan(1.0);
		assertThat(buysFor(answer, aside.getId())).isLessThan(1.0);
	}

	/**
	 * <strong>Decision 6, made executable, and it is the correction this work made to
	 * `roadmap.md`.</strong> An estimate of three identical numbers never varies, so the
	 * contribution ranking reports it as contributing exactly nothing to the spread — and
	 * cutting it removes the same hours from every single run. A shortlist drawn from the
	 * contribution ranking would have hidden the best thing on this plan to drop.
	 */
	@Test
	void workThatNeverVariesContributesNothingAndStillBuysTime() throws Exception {
		WorkItem certain = this.items
			.save(new WorkItem(this.acmePlan, "Twenty hours, we are sure", null, CREATED_AT.plusSeconds(2)));
		estimate(certain, "20.00", "20.00", "20.00");
		this.dependencies.save(new Dependency(this.migration, certain, new BigDecimal("0.00"), CREATED_AT));
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode ranked = parsed(contributions(run).andReturn().getResponse().getContentAsString());
		JsonNode answer = parsed(
				cuts(run, MONDAY.plusDays(9), 80, certain.getId()).andReturn().getResponse().getContentAsString());

		for (JsonNode source : ranked) {
			if (certain.getId().toString().equals(source.get("itemId").asString())) {
				assertThat(source.get("shareOfSpread").asDouble()).isEqualTo(0.0);
			}
		}
		assertThat(buysFor(answer, certain.getId())).isGreaterThan(1.0);
	}

	/**
	 * The date became a number of hours through the run's own calendar, and the answer
	 * says which — the calendar's rule about a stated assumption arriving beside the
	 * number it produced, in the one place where the number is a recommendation.
	 */
	@Test
	void theAnswerSaysWhatTheDateCameTo() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode answer = parsed(cuts(run, MONDAY.plusDays(4), 80).andReturn().getResponse().getContentAsString());

		assertThat(answer.get("targetHours").decimalValue())
			.isEqualByComparingTo(WorkingCalendar.hoursBy(MONDAY, MONDAY.plusDays(4), new BigDecimal(WORKING_DAY)));
		assertThat(answer.get("simulations").asInt()).isEqualTo(1);
		assertThat(answer.get("cuts")).isEmpty();
	}

	/** Asking with nothing to drop is the one question that needs no candidates. */
	@Test
	void aPlanWithPlentyOfTimeSaysTheBarIsAlreadyMet() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		cuts(run, MONDAY.plusDays(200), 80).andExpect(status().isOk())
			.andExpect(jsonPath("$.meets").value(true))
			.andExpect(jsonPath("$.baselineConfidence").value(100.0));
	}

	@Test
	void aRunMadeBeforeThereWasACalendarCannotBeAskedAboutADate() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		this.database.update("update forecast_runs set starts_on = null, working_hours_per_day = null,"
				+ " calendar_rule = null where id = ?", run.getId());

		cuts(run, MONDAY.plusDays(4), 80).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("forecast_has_no_calendar"));
	}

	/**
	 * Work the run was never about cannot be cut from it — and evaluating it anyway would
	 * have answered with the baseline, which reads as "this buys you nothing" rather than
	 * as "this is not what you think it is".
	 */
	@Test
	void refusesToCutWorkTheForecastWasNeverAbout() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		WorkItem since = this.items
			.save(new WorkItem(this.acmePlan, "Written down afterwards", null, CREATED_AT.plusSeconds(9)));

		cuts(run, MONDAY.plusDays(4), 80, since.getId()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("candidate_not_in_forecast"));
	}

	/**
	 * <strong>The two bounds have to agree, and nothing else would notice them
	 * drifting.</strong> Round one of the search is the candidates weighed on their own,
	 * and it is already paid for by the time the budget is first consulted — so a
	 * candidate limit raised past the simulation budget would spend more than the budget
	 * allows without ever reaching the check that says so. The margin has to leave room
	 * for a second round as well, or the search could never take a step it had not
	 * already taken.
	 */
	@Test
	void theCandidateLimitLeavesRoomForTheSearchToRun() {
		assertThat(CutsRequest.MOST_SIMULATIONS).isGreaterThan(CutsRequest.MOST_CANDIDATES + 1);
	}

	/**
	 * <strong>The same work named twice is one candidate.</strong> A second mention asks
	 * no second question, and weighing it twice would spend a simulation to repeat an
	 * answer, put one item in the ranking twice, and let the search "cut" it at two of
	 * its steps — which would read as two sacrifices for the price of one.
	 */
	@Test
	void weighsWorkNamedTwiceOnlyOnce() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode answer = parsed(
				cuts(run, MONDAY.plusDays(2), 100, this.migration.getId(), this.migration.getId(), this.rollout.getId())
					.andReturn()
					.getResponse()
					.getContentAsString());

		assertThat(answer.get("cuts")).hasSize(2);
		// The baseline plus two candidates, not three, and then one more for the second
		// round of the search.
		assertThat(answer.get("simulations").asInt()).isEqualTo(4);
		assertThat(answer.get("together").get("steps")).hasSize(2);
	}

	/**
	 * Each thing weighed costs a whole simulation, so the bound is stated rather than
	 * discovered as a timeout — and refused rather than truncated, since the thirteenth
	 * might have been the one worth dropping.
	 */
	@Test
	void refusesMoreCandidatesThanItWillWeighAtOnce() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		UUID[] tooMany = new UUID[13];
		for (int at = 0; at < tooMany.length; at++) {
			tooMany[at] = this.migration.getId();
		}

		cuts(run, MONDAY.plusDays(4), 80, tooMany).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("too_many_candidates"))
			.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("12")));
	}

	@Test
	void weighingCutsWritesNothing() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		cuts(run, MONDAY.plusDays(4), 80, this.migration.getId()).andExpect(status().isOk());

		assertThat(this.runs.findAll()).singleElement()
			.satisfies((unchanged) -> assertThat(unchanged.getP90Hours()).isEqualByComparingTo(run.getP90Hours()));
	}

	@Test
	void refusesToWeighCutsAgainstARunItNoLongerReproduces() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));
		this.database.update("update forecast_runs set p50_hours = p50_hours + 1 where id = ?", run.getId());

		cuts(run, MONDAY.plusDays(4), 80, this.migration.getId()).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("forecast_replay_mismatch"));
	}

	@Test
	void cannotWeighCutsAgainstAnotherOrganisationsForecast() throws Exception {
		ForecastRun run = forecast(1);

		this.mvc
			.perform(post("/api/forecasts/" + run.getId() + "/cuts")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.grace))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"by\":\"2026-08-21\",\"confidence\":80,\"candidates\":[]}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("forecast_not_found"));
	}

	@Test
	void refusesACutsRequestThatDoesNotSayWhatItWants() throws Exception {
		ForecastRun run = forecast(1);

		this.mvc
			.perform(post("/api/forecasts/" + run.getId() + "/cuts").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"confidence\":80,\"candidates\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.by.code").value("not_null"));
	}

	// A list that gets there ---------------------------------------------------

	/**
	 * <strong>Decision 7, asserted rather than merely warned about.</strong> Two cuts on
	 * one chain overlap: each shortens the same path, so the second buys far less than it
	 * looked worth on its own, and the two singles add up to more than the pair can
	 * possibly deliver. The list therefore reports what was measured with both gone —
	 * which here is a plan with nothing left in it, and so exactly a hundred, an oracle
	 * needing no engine to work out.
	 *
	 * <p>
	 * The first step is checked against its own single as well: round one of the search
	 * <em>is</em> the singles, run once and read twice, so the two numbers must be the
	 * same number rather than two measurements that happen to agree. The candidates are
	 * named smallest first for that check to mean anything — a search that simply took
	 * whatever was offered first would agree with the ranking by accident.
	 */
	@Test
	void twoCutsBuyLessTogetherThanTheSumOfWhatEachBuysAlone() throws Exception {
		this.dependencies.save(new Dependency(this.migration, this.rollout, new BigDecimal("0.00"), CREATED_AT));
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode answer = parsed(
				cuts(run, MONDAY.plusDays(2), 100, this.rollout.getId(), this.migration.getId()).andReturn()
					.getResponse()
					.getContentAsString());

		double baseline = answer.get("baselineConfidence").asDouble();
		JsonNode steps = answer.get("together").get("steps");
		assertThat(answer.get("together").get("ending").asString()).isEqualTo("met");
		assertThat(steps).hasSize(2);
		assertThat(steps.get(1).get("confidence").asDouble()).isEqualTo(100.0);
		assertThat(steps.get(1).get("confidence").asDouble() - baseline)
			.isLessThan(buysFor(answer, this.migration.getId()) + buysFor(answer, this.rollout.getId()));
		JsonNode best = answer.get("cuts").get(0);
		assertThat(best.get("itemId").asString()).isEqualTo(this.migration.getId().toString());
		assertThat(steps.get(0).get("itemId").asString()).isEqualTo(best.get("itemId").asString());
		assertThat(steps.get(0).get("confidence").asDouble()).isEqualTo(best.get("confidence").asDouble());
		// The baseline, both singles, and one more for the second round's single
		// remaining candidate.
		assertThat(answer.get("simulations").asInt()).isEqualTo(4);
	}

	/**
	 * A plan that already clears the bar is told to cut nothing, rather than handed the
	 * least useless thing on its own list. An empty list with a reason is an answer; a
	 * proposal to drop work that buys nothing is advice.
	 */
	@Test
	void aPlanAlreadyPastTheBarIsToldToCutNothing() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		cuts(run, MONDAY.plusDays(200), 80, this.migration.getId()).andExpect(status().isOk())
			.andExpect(jsonPath("$.meets").value(true))
			.andExpect(jsonPath("$.together.steps").isEmpty())
			.andExpect(jsonPath("$.together.ending").value("met"));
	}

	/**
	 * <strong>The ending is as much of the answer as the list is.</strong> Everything on
	 * offer cut and the bar still out of reach is a different answer from a bar that was
	 * met, and the best that could be done is still worth reporting — it is what tells
	 * somebody whether the remaining gap is one more sacrifice or a different date.
	 */
	@Test
	void aBarNothingCanReachSaysSoAndReportsTheBestItCouldDo() throws Exception {
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode answer = parsed(
				cuts(run, MONDAY, 100, this.migration.getId()).andReturn().getResponse().getContentAsString());

		JsonNode steps = answer.get("together").get("steps");
		assertThat(answer.get("together").get("ending").asString()).isEqualTo("nothing_left");
		assertThat(steps).hasSize(1);
		assertThat(steps.get(0).get("itemId").asString()).isEqualTo(this.migration.getId().toString());
		assertThat(steps.get(0).get("confidence").asDouble()).isGreaterThan(answer.get("baselineConfidence").asDouble())
			.isLessThan(100.0);
		assertThat(answer.get("simulations").asInt()).isEqualTo(2);
	}

	/**
	 * <strong>A search that stopped early and did not say so would be reporting the best
	 * thing it happened to look at.</strong> Each round weighs every candidate still in
	 * play, so the work grows with the square of what was offered: twelve candidates cost
	 * thirteen simulations for the first step, eleven more for the second and ten for the
	 * third, and the fourth round would put the request over its budget. It stops there
	 * and says which of the three endings it was.
	 */
	@Test
	void aSearchThatRunsOutOfSimulationsSaysThatIsWhyItStopped() throws Exception {
		List<UUID> candidates = new ArrayList<>(List.of(this.migration.getId(), this.rollout.getId()));
		for (int at = 0; at < 10; at++) {
			WorkItem more = this.items
				.save(new WorkItem(this.acmePlan, "Something else " + at, null, CREATED_AT.plusSeconds(2 + at)));
			estimate(more, "4.00", "8.00", "20.00");
			candidates.add(more.getId());
		}
		ForecastRun run = forecast(assuming(1, 0, 0, 0));

		JsonNode answer = parsed(
				cuts(run, MONDAY, 100, candidates.toArray(new UUID[0])).andReturn().getResponse().getContentAsString());

		assertThat(answer.get("together").get("ending").asString()).isEqualTo("budget_spent");
		assertThat(answer.get("together").get("steps")).hasSize(3);
		assertThat(answer.get("simulations").asInt()).isEqualTo(34);
		assertThat(answer.get("simulations").asInt()).isLessThanOrEqualTo(CutsRequest.MOST_SIMULATIONS);
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
	 * the resource model arrives in, and the day it does is the day the alternative
	 * starts lying quietly.
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

	/**
	 * <strong>Still required, and now the service is what requires it.</strong> Bean
	 * Validation cannot ask this question — whether a capacity is needed depends on
	 * whether the organisation has described its team — so a {@code @NotNull} here would
	 * make the first forecast after describing one a refusal about a field that should no
	 * longer be on the screen at all.
	 */
	@Test
	void refusesAForecastThatSaysNeitherACapacityNorATeam() throws Exception {
		asking(withoutCapacity()).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("capacity_required"));
	}

	/**
	 * <strong>And refuses one that says both.</strong> Refused rather than ignored, which
	 * is the rule {@code progress_not_applicable} states: silently dropping input is
	 * worse than refusing it, because the person is not told they have been overruled.
	 * Two numbers would also leave a reader unable to say which one bound the answer.
	 */
	@Test
	void refusesACapacityFromAnOrganisationThatHasDescribedItsTeam() throws Exception {
		pool("Backend engineers", 3);

		asking(assuming(2, 0, 0, 0)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("capacity_not_applicable"));
	}

	// The team the plan is scheduled against ------------------------------------

	/**
	 * <strong>Capacity is what the pools hold</strong>, derived rather than asked for —
	 * so every screen that prints a run's capacity keeps working, and the column keeps
	 * meaning what it has always meant.
	 */
	@Test
	void aDeclaredTeamIsTheCapacity() throws Exception {
		pool("Backend engineers", 3);
		pool("Staging environment", 1);

		ForecastRun run = forecast(withoutCapacity());

		assertThat(run.getCapacity()).isEqualTo(4);
		assertThat(run.getEngineVersion()).isEqualTo(Engine.VERSION);
	}

	/**
	 * <strong>Work that names nothing is said out loud, and only where it can change the
	 * answer.</strong> With one pool, naming nothing and naming that pool are the same
	 * claim; with two, an unannotated item takes a unit somebody else may have needed.
	 */
	@Test
	void workThatNamesNoResourceIsAReportedLimitation() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		pool("Staging environment", 1);
		needs(this.migration, backend, 1);

		assertThat(reported(forecast(withoutCapacity())).get("limitations").toString()).contains("unassigned_work");
	}

	@Test
	void oneTeamPoolNeedsNothingSaidAboutUnassignedWork() throws Exception {
		pool("Everyone", 3);

		assertThat(reported(forecast(withoutCapacity())).get("limitations").toString())
			.doesNotContain("unassigned_work");
	}

	/**
	 * A resource the team has put away is left out and said out loud, exactly as an arrow
	 * into archived work is: a pool the organisation no longer has cannot be scheduled
	 * against.
	 */
	@Test
	void aRequirementOnAPutAwayResourceIsLeftOutAndReported() throws Exception {
		Resource retired = pool("Contractors", 2);
		pool("Backend engineers", 3);
		needs(this.migration, retired, 1);
		retired.archive(CREATED_AT);
		this.resources.save(retired);

		assertThat(reported(forecast(withoutCapacity())).get("limitations").toString())
			.contains("requirements_on_archived_resources");
	}

	/**
	 * <strong>A pool shrunk below what work already needs of it is refused, not left
	 * out.</strong> The requirement was legal when it was written and the pool got
	 * smaller afterwards, which is a door {@code requirement} cannot watch — and
	 * {@code resource} may not look at what depends on it without pointing an arrow back
	 * the way it came.
	 *
	 * <p>
	 * Dropping the need instead would make the work generic and the forecast sooner than
	 * the plan can possibly be delivered, with nothing on screen looking amiss. Before
	 * this it was neither: {@code Resourcing} refused it with an
	 * {@code IllegalArgumentException} nothing handled, so every forecast of the plan
	 * answered 500 with no {@code code} at all.
	 */
	@Test
	void refusesAPlanNeedingMoreOfAPoolThanTheTeamStillHas() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		needs(this.migration, backend, 3);
		backend.describe("Backend engineers", 1, null);
		this.resources.save(backend);

		asking(withoutCapacity()).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("work_needs_more_than_the_team_has"));

		assertThat(this.runs.findAll()).isEmpty();
	}

	/**
	 * <strong>What a run was scheduled against travels with the number</strong>, which is
	 * the rule the five assumptions and the calendar already keep. The units are the
	 * run's own and the name is today's — a pool renamed since is not a thing that moved,
	 * so the run stores an identifier and the name comes off the organisation's list.
	 */
	@Test
	void aRunSaysWhichTeamItWasScheduledAgainst() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		pool("Staging environment", 1);

		JsonNode answer = reported(forecast(withoutCapacity()));

		assertThat(answer.get("resources").size()).isEqualTo(2);
		assertThat(answer.get("resources").get(0).get("resourceId").asString()).isEqualTo(backend.getId().toString());
		assertThat(answer.get("resources").get(0).get("name").asString()).isEqualTo("Backend engineers");
		assertThat(answer.get("resources").get(0).get("units").asInt()).isEqualTo(3);
		assertThat(answer.get("resources").get(0).get("archived").asBoolean()).isFalse();
	}

	/**
	 * <strong>And it keeps saying what it assumed after the team changes.</strong> Hiring
	 * somebody must not silently rewrite what last month's forecast was scheduled against
	 * — which is the whole reason the declaration is copied onto the run rather than read
	 * from the organisation when somebody looks.
	 */
	@Test
	void changingTheTeamDoesNotChangeWhatAnOldRunAssumed() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		ForecastRun run = forecast(withoutCapacity());
		backend.describe("Platform engineers", 9, null);
		this.resources.save(backend);

		JsonNode answer = reported(run);

		assertThat(answer.get("capacity").asInt()).isEqualTo(3);
		assertThat(answer.get("resources").get(0).get("units").asInt()).isEqualTo(3);
		// The name is the one thing that is today's, because a rename is not a thing that
		// moved — the same split a contribution ranking makes for the work it names.
		assertThat(answer.get("resources").get(0).get("name").asString()).isEqualTo("Platform engineers");
	}

	/** A pool put away since is named and marked, rather than rendering as a blank. */
	@Test
	void aPoolPutAwaySinceIsNamedAndMarked() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		ForecastRun run = forecast(withoutCapacity());
		backend.archive(CREATED_AT);
		this.resources.save(backend);

		JsonNode answer = reported(run);

		assertThat(answer.get("resources").get(0).get("archived").asBoolean()).isTrue();
		assertThat(answer.get("resources").get(0).get("name").asString()).isEqualTo("Backend engineers");
	}

	/**
	 * <strong>A pool this organisation no longer holds at all is named as such.</strong>
	 * A guard rather than a scenario — nothing in this product deletes a resource, they
	 * are put away — and it is here for {@code ForecastResponse.dateOf}'s reason: the row
	 * is made the way something outside this application would leave it, so that the day
	 * one exists it is described rather than rendered as a blank beside a number.
	 */
	@Test
	void aPoolThisOrganisationNoLongerHoldsIsSaidRatherThanBlank() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		ForecastRun run = forecast(withoutCapacity());
		this.database.update("delete from resources where id = ?", backend.getId());

		JsonNode answer = reported(run);

		assertThat(answer.get("resources").size()).isEqualTo(1);
		assertThat(answer.get("resources").get(0).get("name").isNull()).isTrue();
		assertThat(answer.get("resources").get(0).get("units").asInt()).isEqualTo(3);
	}

	/**
	 * A run made before this work stored a declaration at all reports no team, which is
	 * the absence {@code V19} deliberately did not backfill: such a run did not assume an
	 * empty team, it assumed no such concept.
	 */
	@Test
	void aRunFromBeforeTheColumnExistedReportsNoTeam() throws Exception {
		ForecastRun run = forecast(2);
		this.database.update("update forecast_runs set resourcing = null where id = ?", run.getId());

		JsonNode answer = reported(run);

		assertThat(answer.get("resources").isEmpty()).isTrue();
		assertThat(answer.get("capacity").asInt()).isEqualTo(2);
	}

	/** And a run made against no team at all says so with an empty list, not a null. */
	@Test
	void aRunAgainstNoTeamHasNoResources() throws Exception {
		JsonNode answer = reported(forecast(2));

		assertThat(answer.get("resources").isArray()).isTrue();
		assertThat(answer.get("resources").isEmpty()).isTrue();
	}

	// What if we hire somebody? -------------------------------------------------

	/**
	 * <strong>A pool the plan is actually waiting for buys something, and the second one
	 * buys less.</strong> That diminishing return is the answer to "should we hire" — the
	 * first person is worth more than the second, and how much more is the thing nobody
	 * can feel their way to.
	 */
	@Test
	void hiringIntoTheBindingPoolBuysTimeAndBuysLessTheSecondTime() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		needs(this.migration, backend, 1);
		needs(this.rollout, backend, 1);
		ForecastRun run = forecast(withoutCapacity());

		JsonNode answer = hires(run, backend, 3);

		JsonNode steps = at(answer, 80).get("hires");
		assertThat(steps.size()).isEqualTo(3);
		assertThat(steps.get(0).get("daysEarlier").asInt()).isPositive();
		// Cumulative, so the rows climb — and what each extra person adds does not.
		int first = steps.get(0).get("daysEarlier").asInt();
		int second = steps.get(1).get("daysEarlier").asInt() - first;
		assertThat(second).isLessThan(first);
		assertThat(answer.get("simulations").asInt()).isEqualTo(4);
	}

	/**
	 * <strong>A pool nothing is waiting for buys nothing, and says so with a
	 * zero.</strong> Which is the whole reason this is simulated rather than reasoned
	 * about: the pool with the fewest units is not necessarily the one holding the plan
	 * up.
	 */
	@Test
	void hiringIntoAPoolNothingIsWaitingForBuysNothing() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		Resource idle = pool("Technical writers", 4);
		needs(this.migration, backend, 1);
		needs(this.rollout, backend, 1);
		ForecastRun run = forecast(withoutCapacity());

		JsonNode steps = at(hires(run, idle, 2), 80).get("hires");

		assertThat(steps.get(0).get("daysEarlier").asInt()).isZero();
		assertThat(steps.get(1).get("daysEarlier").asInt()).isZero();
	}

	/**
	 * <strong>And a plan held up by its own order buys nothing from anybody</strong>,
	 * which is the case worth being able to demonstrate: no amount of hiring shortens a
	 * chain, and a team that believes otherwise is about to spend money on it.
	 */
	@Test
	void hiringBuysNothingWhereADependencyChainDecidesTheFinish() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		needs(this.migration, backend, 1);
		needs(this.rollout, backend, 1);
		this.dependencies.save(new Dependency(this.migration, this.rollout, BigDecimal.ZERO, CREATED_AT));
		ForecastRun run = forecast(withoutCapacity());

		JsonNode steps = at(hires(run, backend, 2), 80).get("hires");

		assertThat(steps.get(0).get("daysEarlier").asInt()).isZero();
		assertThat(steps.get(1).get("daysEarlier").asInt()).isZero();
	}

	/**
	 * Every confidence at once, because the control moves and the simulations are bought.
	 */
	@Test
	void hiringIsAnsweredAtEveryConfidenceTheControlOffers() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		needs(this.migration, backend, 1);
		ForecastRun run = forecast(withoutCapacity());

		JsonNode answer = hires(run, backend, 1);

		assertThat(answer.get("at").size()).isEqualTo(3);
		for (int confidence : new int[] { 50, 80, 95 }) {
			assertThat(at(answer, confidence).get("stands").isNull()).as("%d%%", confidence).isFalse();
		}
	}

	/**
	 * A run scheduled against a capacity is refused rather than answered: "one more" is
	 * the question the forecast form already asks one field away, and what this exists
	 * for is <em>which</em> pool — which only means something once a team has been
	 * described.
	 */
	@Test
	void aRunAgainstACapacityHasNoResourceToAddTo() throws Exception {
		// The run first and the pool second, because the two cannot coexist the other way
		// round: once a team is described, naming a capacity is refused.
		ForecastRun run = forecast(2);
		Resource backend = pool("Backend engineers", 1);

		asking(run, backend.getId(), 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("forecast_has_no_resources"));
	}

	/** And a pool declared since the run is a question about a team nobody had. */
	@Test
	void aPoolDeclaredSinceTheRunIsNotInIt() throws Exception {
		pool("Backend engineers", 1);
		ForecastRun run = forecast(withoutCapacity());
		Resource later = pool("Designers", 2);

		asking(run, later.getId(), 1).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("resource_not_in_forecast"));
	}

	/**
	 * A pool belonging to somebody else is not there at all, which is the isolation rule.
	 */
	@Test
	void anotherOrganisationsPoolIsNotSomethingToHireInto() throws Exception {
		pool("Backend engineers", 1);
		ForecastRun run = forecast(withoutCapacity());

		asking(run, UUID.randomUUID(), 1).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("resource_not_found"));
	}

	@Test
	void hiringIntoARunWithNoCalendarHasNoDateToMove() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		ForecastRun run = forecast(withoutCapacity());
		madeBeforeThereWasACalendar(run.getId());

		asking(run, backend.getId(), 1).andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.code").value("forecast_has_no_calendar"));
	}

	@Test
	void refusesMoreHiresThanItWillWeigh() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		ForecastRun run = forecast(withoutCapacity());

		asking(run, backend.getId(), 11).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.units.code").value("max"));
		asking(run, backend.getId(), 0).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.units.code").value("positive"));
	}

	/**
	 * <strong>Nothing is written.</strong> Forty simulations can go past for one question
	 * without {@code forecast_runs} gaining a row, which is what keeps that table meaning
	 * one thing — somebody asked the engine — and is what the drift detector walks.
	 */
	@Test
	void weighingAHireWritesNothing() throws Exception {
		Resource backend = pool("Backend engineers", 1);
		ForecastRun run = forecast(withoutCapacity());
		long before = this.runs.count();

		hires(run, backend, 3);

		assertThat(this.runs.count()).isEqualTo(before);
	}

	/**
	 * <strong>A snapshot written before there was a team to describe reads back as one
	 * pool.</strong> Jackson hands a missing field back as null and a run stored in July
	 * is not going to grow one, so this is the shape every forecast this product has
	 * already made comes back in — and it has to come back as the capacity it was asked
	 * for rather than as nothing at all.
	 */
	@Test
	void aSnapshotFromBeforeResourcesReadsBackAsOnePool() {
		String written = """
				{"items":[{"id":"11111111-1111-1111-1111-111111111111","status":"NOT_STARTED",\
				"spentHours":null,"estimates":[]}],"edges":[]}""";

		ForecastInputs inputs = ForecastSnapshots.read(written, ForecastInputs.class);

		assertThat(inputs.pools()).isEmpty();
		assertThat(inputs.needs()).isEmpty();
		// One pool, holding the capacity the run stored — which `ResourcingTests` pins
		// the
		// other half of, since the units a pool holds are that class's own business.
		assertThat(inputs.toResourcing(3).pools()).isEqualTo(1);
		assertThat(inputs.toResourcing(3).items()).isEqualTo(1);
	}

	/**
	 * Work that has been put away is not forecast, so what it needed is not read either —
	 * and it is not reported, because the item's absence is the answer rather than a
	 * limitation of one.
	 */
	@Test
	void whatPutAwayWorkNeedsIsNotScheduledAgainst() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		needs(this.rollout, backend, 3);
		this.rollout.archive(CREATED_AT);
		this.items.save(this.rollout);

		JsonNode answer = reported(forecast(withoutCapacity()));

		assertThat(answer.get("limitations").toString()).doesNotContain("requirements_on_archived_resources");
		assertThat(answer.get("capacity").asInt()).isEqualTo(3);
	}

	/** And a plan where everything names something says nothing about unassigned work. */
	@Test
	void aPlanThatNamesResourcesEverywhereReportsNoUnassignedWork() throws Exception {
		Resource backend = pool("Backend engineers", 3);
		Resource staging = pool("Staging environment", 1);
		needs(this.migration, backend, 1);
		needs(this.rollout, staging, 1);

		assertThat(reported(forecast(withoutCapacity())).get("limitations").toString())
			.doesNotContain("unassigned_work");
	}

	/**
	 * <strong>The containment, over the wire.</strong> A run this engine made before the
	 * resource model is one with no team declared and a version of 2 — and replaying it
	 * has to reproduce it exactly, or the contribution ranking, the inverse query's cuts
	 * and the movement decomposition all stop answering for every forecast this product
	 * made before today.
	 */
	@Test
	void aRunMadeByTheEngineBeforeResourcesStillReplays() throws Exception {
		ForecastRun run = forecast(2);
		this.database.update("update forecast_runs set engine_version = 2 where id = ?", run.getId());

		contributions(run).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(greaterThan(0)));
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
			.andExpect(jsonPath("$.runs.length()").value(2))
			.andExpect(jsonPath("$.runs[0].id").value(second.getId().toString()))
			.andExpect(jsonPath("$.runs[1].id").value(first.getId().toString()));
	}

	// Whether it keeps moving out ---------------------------------------------

	/**
	 * <strong>The verdict rides on the listing rather than on a request of its
	 * own</strong> — it belongs to the sequence and not to any run in it, and a screen
	 * already drawing that history should not have to ask twice for the one line worth
	 * reading out loud. It costs no simulation: every date it reads is one the same
	 * answer already carries.
	 */
	@Test
	void theListingSaysHowFarTheDateHasDriftedAtEachConfidence() throws Exception {
		ForecastRun first = forecast(2);
		ForecastRun second = forecast(2);

		listed().andExpect(jsonPath("$.drift.runs").value(2))
			.andExpect(jsonPath("$.drift.sinceRunId").value(first.getId().toString()))
			.andExpect(jsonPath("$.drift.at.length()").value(3))
			.andExpect(jsonPath("$.drift.at[1].confidence").value(80))
			.andExpect(jsonPath("$.drift.at[1].fromDate").value(dateOf(first, 80)))
			.andExpect(jsonPath("$.drift.at[1].toDate").value(dateOf(second, 80)))
			.andExpect(jsonPath("$.drift.at[1].bandDays").isNumber());
	}

	/**
	 * A plan forecast once has drifted nought days from itself, which is an answer rather
	 * than an absence — and one the flag can be read against without a second rule for
	 * the case where there is nothing behind it.
	 */
	@Test
	void aPlanForecastOnceHasNotDriftedFromAnything() throws Exception {
		ForecastRun only = forecast(2);

		listed().andExpect(jsonPath("$.drift.runs").value(1))
			.andExpect(jsonPath("$.drift.sinceRunId").value(only.getId().toString()))
			.andExpect(jsonPath("$.drift.at[1].days").value(0))
			.andExpect(jsonPath("$.drift.at[1].movingOut").value(false));
	}

	/** And a plan nobody has forecast has no date to have moved. */
	@Test
	void aPlanWithNoForecastsHasNoDrift() throws Exception {
		listed().andExpect(jsonPath("$.runs.length()").value(0)).andExpect(jsonPath("$.drift").doesNotExist());
	}

	/**
	 * <strong>Somebody halved the capacity, and the window starts again.</strong> The
	 * date moved because the question changed, and drift measured across that boundary is
	 * `roadmap.md`'s own warning — a slide that never happened, reported as one.
	 */
	@Test
	void aChangedAssumptionStartsANewWindow() throws Exception {
		forecast(1);
		forecast(1);
		ForecastRun roomier = forecast(4);

		listed().andExpect(jsonPath("$.drift.runs").value(1))
			.andExpect(jsonPath("$.drift.sinceRunId").value(roomier.getId().toString()))
			.andExpect(jsonPath("$.drift.at[1].days").value(0));
	}

	/**
	 * A plan that has added a fortnight of work to a two-day band since its first
	 * forecast is a plan that is sliding, and this is the whole feature in one case: the
	 * same arithmetic reports nothing at all above, on a history that only re-ran itself.
	 */
	@Test
	void aPlanThatKeepsGrowingIsFlagged() throws Exception {
		forecast(1);
		WorkItem discovered = this.items.save(new WorkItem(this.acmePlan, "Nobody had listed this", null, CREATED_AT));
		estimate(discovered, "300.00", "320.00", "360.00");
		forecast(1);

		listed().andExpect(jsonPath("$.drift.runs").value(2))
			.andExpect(jsonPath("$.drift.at[1].movingOut").value(true));
	}

	/**
	 * A run made before there was a calendar has hours and no date, so there are no days
	 * to have drifted — the same absence the run's own five dates report, and for `V14`'s
	 * reason.
	 */
	@Test
	void aHistoryWithNoCalendarHasNoDaysToDrift() throws Exception {
		ForecastRun first = forecast(2);
		ForecastRun second = forecast(2);
		madeBeforeThereWasACalendar(first.getId());
		madeBeforeThereWasACalendar(second.getId());

		listed().andExpect(jsonPath("$.drift.runs").value(2))
			.andExpect(jsonPath("$.drift.at[1].days").doesNotExist())
			.andExpect(jsonPath("$.drift.at[1].bandDays").doesNotExist())
			.andExpect(jsonPath("$.drift.at[1].movingOut").value(false));
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
	 * The assertion the the plan schema review found missing from
	 * {@code EstimateApiTests}, written here rather than inherited: an identity token
	 * names a person and no organisation, and a forecast is tenant-owned data.
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
	 * growth, which is what the engine did before the common-cause model and is now
	 * something somebody has to say.
	 */
	private ForecastRun forecast(int capacity) throws Exception {
		return forecast(assuming(capacity, 0, 0, 0));
	}

	/**
	 * The body of a request for a forecast. Three of a run's five assumptions became
	 * required in the common-cause model, so there is no shorter honest way to ask for
	 * one.
	 */
	private static String assuming(int capacity, Number worseByPercent, Number growthP10, Number growthP90) {
		return assuming(capacity, worseByPercent, growthP10, growthP90, null);
	}

	/**
	 * The same, for the one field a caller may leave out — {@code null} asks for the
	 * ordinary ten thousand runs by saying nothing, which is a different request from one
	 * naming a number and has to be sent as one.
	 */
	/**
	 * Everything a forecast needs except the capacity, which a declared team supplies.
	 */
	private static String withoutCapacity() {
		return """
				{"teamFactorWorseByPercent":0,"scopeGrowthP10Percent":0,"scopeGrowthP90Percent":0,\
				"startsOn":"%s","workingHoursPerDay":%s}""".formatted(MONDAY, WORKING_DAY);
	}

	private ResultActions asking(ForecastRun run, UUID resourceId, int units) throws Exception {
		return this.mvc.perform(
				post("/api/forecasts/" + run.getId() + "/hires").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"resourceId\":\"" + resourceId + "\",\"units\":" + units + "}"));
	}

	private JsonNode hires(ForecastRun run, Resource pool, int units) throws Exception {
		return parsed(asking(run, pool.getId(), units).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
	}

	/** One confidence's answer out of the three every reply carries. */
	private static JsonNode at(JsonNode answer, int confidence) {
		for (JsonNode reading : answer.get("at")) {
			if (reading.get("confidence").asInt() == confidence) {
				return reading;
			}
		}
		throw new AssertionError("no answer at " + confidence + "%");
	}

	private ResultActions asking(String body) throws Exception {
		return this.mvc.perform(post("/api/projects/" + this.acmePlan.getId() + "/forecasts")
			.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	/**
	 * A pool, declared a second after the one before it.
	 *
	 * <p>
	 * <strong>The instants are distinct on purpose.</strong> Declaration order is part of
	 * the model — work that names no resource takes one unit of the first pool with one
	 * free — and pools declared in the same instant fall back to their identifiers, which
	 * is a total order for any one set of rows and an arbitrary one between two fixtures.
	 * A test that made two at once passed and failed on alternate runs.
	 */
	private Resource pool(String name, int units) {
		return this.resources
			.save(new Resource(this.acmePlan.getTenant(), name, units, null, CREATED_AT.plusSeconds(this.declared++)));
	}

	private void needs(WorkItem item, Resource resource, int units) {
		this.requirements.save(new Requirement(this.acmePlan.getTenant(), item, resource, units, CREATED_AT));
	}

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

	private ResultActions cuts(ForecastRun run, LocalDate by, int confidence, UUID... candidates) throws Exception {
		StringBuilder named = new StringBuilder();
		for (UUID candidate : candidates) {
			named.append(named.isEmpty() ? "" : ",").append('"').append(candidate).append('"');
		}
		return this.mvc
			.perform(post("/api/forecasts/" + run.getId() + "/cuts").header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"by\":\"" + by + "\",\"confidence\":" + confidence + ",\"candidates\":[" + named + "]}"));
	}

	/** What the answer says one candidate is worth, in percentage points. */
	private static double buysFor(JsonNode answer, UUID itemId) {
		for (JsonNode cut : answer.get("cuts")) {
			if (itemId.toString().equals(cut.get("itemId").asString())) {
				return cut.get("buys").asDouble();
			}
		}
		throw new AssertionError("no cut was weighed for " + itemId);
	}

	private ResultActions contributions(ForecastRun run) throws Exception {
		return this.mvc.perform(get("/api/forecasts/" + run.getId() + "/contributions")
			.header(HttpHeaders.AUTHORIZATION, bearer(this.ada)));
	}

	private ResultActions listed() throws Exception {
		return this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/forecasts")
			.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))).andExpect(status().isOk());
	}

	/**
	 * The day a stored run puts one confidence on, worked out here the way the response
	 * works it out — so a case asserting where a drift was measured from cannot pass
	 * because both ends read the same wrong calendar.
	 */
	private static String dateOf(ForecastRun run, int confidence) {
		BigDecimal hours = switch (confidence) {
			case 50 -> run.getP50Hours();
			case 80 -> run.getP80Hours();
			default -> run.getP95Hours();
		};
		return WorkingCalendar.finishOn(run.getStartsOn(), hours, run.getWorkingHoursPerDay()).toString();
	}

	/**
	 * A calendar is required of every run made today, so the only way to have one without
	 * is to be older than the calendar — which is what {@code V14} left, and what this
	 * reproduces.
	 */
	private void madeBeforeThereWasACalendar(UUID runId) {
		this.database.update("update forecast_runs set starts_on = null, working_hours_per_day = null,"
				+ " calendar_rule = null where id = ?", runId);
	}

	private JsonNode parsed(String body) {
		return this.json.readTree(body);
	}

	private static List<String> itemIdsIn(JsonNode ranked) {
		List<String> ids = new ArrayList<>();
		for (JsonNode source : ranked) {
			ids.add(source.get("itemId").asString());
		}
		return ids;
	}

	private static List<String> titlesIn(JsonNode ranked) {
		List<String> titles = new ArrayList<>();
		for (JsonNode source : ranked) {
			titles.add(source.get("title").asString());
		}
		return titles;
	}

	private static List<String> kindsIn(JsonNode ranked) {
		List<String> kinds = new ArrayList<>();
		for (JsonNode source : ranked) {
			kinds.add(source.get("kind").asString());
		}
		return kinds;
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
