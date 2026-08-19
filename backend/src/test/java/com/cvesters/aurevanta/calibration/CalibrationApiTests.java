package com.cvesters.aurevanta.calibration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.estimate.Elicitation;
import com.cvesters.aurevanta.estimate.Estimate;
import com.cvesters.aurevanta.estimate.EstimateRepository;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemProgress;
import com.cvesters.aurevanta.item.WorkItemProgressRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The organisation's record, over the wire.
 *
 * <p>
 * The bucketing rules are settled next door in {@code CalibrationServiceTests} and the
 * arithmetic in {@code CalibrationTests}; what is left for this class is what the wire
 * adds. Three of those are worth reading. <strong>Nulls rather than zeros</strong>,
 * because "0% of your estimates landed inside their range" is the single worst thing this
 * API could send to an organisation that has not finished anything. <strong>The split by
 * method</strong>, which is the only answer elicitation's question will ever get. And
 * <strong>name order and not rank order</strong>, because a hit-rate leaderboard is won
 * by writing one-to-a-thousand.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CalibrationApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-06-01T08:00:00Z");

	private static final LocalDate BEGAN = LocalDate.parse("2026-07-10");

	private static final LocalDate FINISHED = LocalDate.parse("2026-07-20");

	private static final Instant FORESEEN = Instant.parse("2026-07-01T09:00:00Z");

	private static final Instant LATER = Instant.parse("2026-07-05T09:00:00Z");

	private static final Instant IN_HINDSIGHT = Instant.parse("2026-07-18T09:00:00Z");

	private static final BigDecimal P10 = new BigDecimal("10.00");

	private static final BigDecimal P90 = new BigDecimal("40.00");

	private static final BigDecimal HIT = new BigDecimal("20.00");

	private static final BigDecimal MISS = new BigDecimal("100.00");

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
	private WorkItemProgressRepository reports;

	@Autowired
	private EstimateRepository estimates;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership linus;

	private Membership grace;

	private Project plan;

	@BeforeEach
	void seedAnOrganisationWithAPlanInIt() {
		this.estimates.deleteAll();
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		// Zara sorts after Ada by name and is created first, so name order and arrival
		// order disagree — otherwise the ordering assertion below would pass either way.
		this.linus = member(user("zara@acme.test", "Zara"), acme, UserRole.MEMBER);
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		this.plan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
	}

	// Nothing to report, which is most organisations for most of a year ---------

	/**
	 * <strong>The state this endpoint spends its first year in, and the one worth
	 * designing.</strong> Every count is a true zero and every rate is absent, because
	 * nought out of nought is not a rate of nought.
	 */
	@Test
	void anOrganisationThatHasFinishedNothingSaysSoAndDoesNotScoreZero() throws Exception {
		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.forecasts.scored").value(0))
			.andExpect(jsonPath("$.forecasts.hits").value(0))
			// One absent object rather than five absent fields, which is the whole of why
			// they were grouped: there is no shape here that holds a rate and not its
			// bounds, so no client can render the half that means nothing on its own.
			.andExpect(jsonPath("$.forecasts.rate").value(nullValue()))
			.andExpect(jsonPath("$.forecasts.corrections").value(nullValue()))
			.andExpect(jsonPath("$.byEstimator", hasSize(0)))
			.andExpect(jsonPath("$.byMethod", hasSize(0)))
			.andExpect(jsonPath("$.firstScored").value(nullValue()))
			.andExpect(jsonPath("$.lastScored").value(nullValue()));
	}

	/**
	 * The two reasons nothing is scored are different things to go and do about it, so
	 * they are two numbers rather than one.
	 */
	@Test
	void saysWhichHalfOfTheEvidenceIsMissing() throws Exception {
		WorkItem unmeasured = finished("Nobody timed it", null);
		estimated(unmeasured, this.ada, FORESEEN);
		finished("Nobody predicted it", HIT);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.coverage.completedItems").value(2))
			.andExpect(jsonPath("$.coverage.withActual").value(1))
			.andExpect(jsonPath("$.coverage.withEstimate").value(1))
			.andExpect(jsonPath("$.coverage.scoredItems").value(0))
			.andExpect(jsonPath("$.coverage.movedByTheStartDay").value(0));
	}

	// The record itself --------------------------------------------------------

	/**
	 * The rate never travels without the interval, because four hits out of five is 80%
	 * and says nothing at all — and never without the multiplier, because a rate alone is
	 * won by widening every range until it always contains the outcome.
	 */
	@Test
	void theRateArrivesWithBothOfTheThingsThatStopItBeingMisread() throws Exception {
		estimated(finished("Hit it", HIT), this.ada, FORESEEN);
		estimated(finished("Missed it", MISS), this.ada, FORESEEN);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.forecasts.scored").value(2))
			.andExpect(jsonPath("$.forecasts.hits").value(1))
			.andExpect(jsonPath("$.forecasts.rate.value").value(0.5))
			.andExpect(jsonPath("$.forecasts.rate.low").isNumber())
			.andExpect(jsonPath("$.forecasts.rate.high").isNumber())
			.andExpect(jsonPath("$.forecasts.corrections.medianPercentile").isNumber())
			.andExpect(jsonPath("$.forecasts.corrections.bandWidthMultiplier").isNumber())
			.andExpect(jsonPath("$.forecasts.aboveP90").value(1))
			.andExpect(jsonPath("$.forecasts.belowP10").value(0))
			.andExpect(jsonPath("$.forecasts.pointEstimates").value(0));
	}

	/**
	 * A single outcome has a rate and cannot correct anything, and says so field by
	 * field.
	 */
	@Test
	void aRateWithTooLittleBehindItStillWithholdsTheCorrections() throws Exception {
		estimated(finished("Hit it", HIT), this.ada, FORESEEN);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.forecasts.rate.value").value(1.0))
			.andExpect(jsonPath("$.forecasts.rate.low").isNumber())
			.andExpect(jsonPath("$.forecasts.corrections").value(nullValue()));
	}

	/** The three buckets are three answers and the response keeps them apart. */
	@Test
	void theThreeBucketsArrivesSeparatelyAndDoNotAddUp() throws Exception {
		estimated(finished("Foreseen", MISS), this.ada, FORESEEN);
		estimated(finished("In hindsight", HIT), this.ada, IN_HINDSIGHT);
		WorkItem noStart = this.items.save(new WorkItem(this.plan, "Ticked off in one go", null, CREATED_AT));
		noStart.recordProgress(WorkItemStatus.DONE, null, FINISHED, HIT);
		this.items.save(noStart);
		estimated(noStart, this.ada, FORESEEN);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.forecasts.scored").value(1))
			.andExpect(jsonPath("$.forecasts.rate.value").value(0.0))
			.andExpect(jsonPath("$.reports.scored").value(1))
			.andExpect(jsonPath("$.reports.rate.value").value(1.0))
			.andExpect(jsonPath("$.unbounded.scored").value(1))
			.andExpect(jsonPath("$.unbounded.rate.value").value(1.0));
	}

	/**
	 * Moments the server observed, not days anybody reported — so they go out as instants
	 * and a client converts them to where its reader is sitting. They are what tells a
	 * current record from one describing how the team estimated a year ago.
	 */
	@Test
	void theSpanIsWhenTheScoredEstimatesWereWritten() throws Exception {
		WorkItem item = finished("Migrate the auth service", HIT);
		estimated(item, this.ada, FORESEEN);
		estimated(finished("Write the runbook", HIT), this.linus, LATER);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.firstScored").value("2026-07-01T09:00:00Z"))
			.andExpect(jsonPath("$.lastScored").value("2026-07-05T09:00:00Z"));
	}

	// The two breakdowns -------------------------------------------------------

	/**
	 * <strong>The split {@code V15} exists for</strong>, and the only evidence
	 * elicitation's claim will ever have: did changing how the question is put change how
	 * often the answer contained the truth?
	 */
	@Test
	void splitsTheForecastsByHowTheRangeWasAskedFor() throws Exception {
		estimateWith(finished("Asked the old way", MISS), this.ada, Elicitation.THREE_POINT);
		estimateWith(finished("Asked the new way", HIT), this.ada, Elicitation.SURPRISE_FRAMED);
		estimateWith(finished("Asked the new way again", HIT), this.linus, Elicitation.SURPRISE_FRAMED);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.byMethod", hasSize(2)))
			.andExpect(jsonPath("$.byMethod[0].method").value("surprise_framed"))
			.andExpect(jsonPath("$.byMethod[0].record.scored").value(2))
			.andExpect(jsonPath("$.byMethod[0].record.rate.value").value(1.0))
			.andExpect(jsonPath("$.byMethod[1].method").value("three_point"))
			.andExpect(jsonPath("$.byMethod[1].record.scored").value(1))
			.andExpect(jsonPath("$.byMethod[1].record.rate.value").value(0.0));
	}

	/**
	 * <strong>Name order, never rank order.</strong> This product ranks work and not
	 * people: a hit rate sorted best-first is a leaderboard, and a hit-rate leaderboard
	 * is won by estimating one to a thousand hours, which is the failure the record
	 * exists to expose.
	 */
	@Test
	void namesTheEstimatorsInNameOrderAndNotInRankOrder() throws Exception {
		// Zara scores 100% and Ada 0%, so a list sorted by rate would put Zara first.
		estimated(finished("Zara was right", HIT), this.linus, FORESEEN);
		estimated(finished("Ada was wrong", MISS), this.ada, FORESEEN);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.byEstimator", hasSize(2)))
			.andExpect(jsonPath("$.byEstimator[0].estimatorName").value("Ada"))
			.andExpect(jsonPath("$.byEstimator[0].estimatorId").value(this.ada.getUser().getId().toString()))
			.andExpect(jsonPath("$.byEstimator[0].record.rate.value").value(0.0))
			.andExpect(jsonPath("$.byEstimator[1].estimatorName").value("Zara"))
			.andExpect(jsonPath("$.byEstimator[1].record.rate.value").value(1.0));
	}

	/**
	 * Both breakdowns attribute forecasts and nothing else: crediting a report to a
	 * person would measure how late they file rather than how well they predict.
	 */
	@Test
	void theBreakdownsCountForecastsAndNothingElse() throws Exception {
		estimated(finished("In hindsight", HIT), this.ada, IN_HINDSIGHT);

		read(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.reports.scored").value(1))
			.andExpect(jsonPath("$.byEstimator", hasSize(0)))
			.andExpect(jsonPath("$.byMethod", hasSize(0)));
	}

	// Whose record it is -------------------------------------------------------

	@Test
	void anotherOrganisationsEvidenceIsInvisible() throws Exception {
		estimated(finished("Migrate the auth service", MISS), this.ada, FORESEEN);

		read(this.grace).andExpect(status().isOk())
			.andExpect(jsonPath("$.forecasts.scored").value(0))
			.andExpect(jsonPath("$.coverage.completedItems").value(0));
	}

	/** Every member may see it, for the reason every member may see the member list. */
	@Test
	void anyMemberMayReadIt() throws Exception {
		estimated(finished("Migrate the auth service", HIT), this.ada, FORESEEN);

		read(this.linus).andExpect(status().isOk()).andExpect(jsonPath("$.forecasts.scored").value(1));
	}

	@Test
	void isRefusedToSomebodyWhoNoLongerBelongs() throws Exception {
		String token = bearer(this.linus);
		this.memberships.delete(this.linus);

		this.mvc.perform(get("/api/calibration").header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/calibration")).andExpect(status().isUnauthorized());
	}

	// Fixtures -----------------------------------------------------------------

	private ResultActions read(Membership caller) throws Exception {
		return this.mvc.perform(get("/api/calibration").header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private WorkItem finished(String title, BigDecimal actualHours) {
		WorkItem item = this.items.save(new WorkItem(this.plan, title, null, CREATED_AT));
		item.recordProgress(WorkItemStatus.DONE, BEGAN, FINISHED, actualHours);
		this.items.save(item);
		this.reports.save(new WorkItemProgress(item, this.ada.getUser(), WorkItemStatus.DONE, BEGAN, FINISHED,
				actualHours, CREATED_AT));
		return item;
	}

	private void estimated(WorkItem item, Membership by, Instant at) {
		this.estimates.save(new Estimate(item, by.getUser(), P10, HIT, P90, Elicitation.SURPRISE_FRAMED, at));
	}

	private void estimateWith(WorkItem item, Membership by, String method) {
		this.estimates.save(new Estimate(item, by.getUser(), P10, HIT, P90, method, FORESEEN));
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
