package com.cvesters.aurevanta.forecast;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
import com.cvesters.aurevanta.forecast.model.Throughput;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a plan's own history says, over the wire.
 *
 * <p>
 * The arithmetic is settled in {@code ThroughputTests} and the reading of a plan in
 * {@code PlanHistoryTests}; what is left for this class is what the boundary adds.
 * <strong>The window ships whether or not the projection does</strong>, which is the
 * shape of every "not enough yet" state in this product, and there are three separate
 * ways to have no projection — each of which says which one it is rather than answering
 * with an absence.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ThroughputApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-01-05T08:00:00Z");

	/** A Monday, so the week boundaries in these fixtures are somewhere obvious. */
	private static final LocalDate WEEK_ONE = LocalDate.parse("2026-01-05");

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
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership grace;

	private Project plan;

	@BeforeEach
	void seedAPlanWithSomeHistoryInIt() {
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		this.plan = this.projects.save(new Project(acme, "Q1 platform work", null, CREATED_AT));
	}

	// A history worth projecting from ------------------------------------------

	/**
	 * Twenty weeks of five a week and forty items left: the oracle, over the wire. Every
	 * run answers eight weeks, so every percentile is eight and every date is eight weeks
	 * past the day being asked about.
	 */
	@Test
	void answersWhatThePlansOwnHistorySays() throws Exception {
		steady(5, 20);
		remaining(40);
		LocalDate asOf = WEEK_ONE.plusWeeks(19);

		read(this.ada, asOf).andExpect(status().isOk())
			.andExpect(jsonPath("$.projectId").value(this.plan.getId().toString()))
			.andExpect(jsonPath("$.asOf").value(asOf.toString()))
			.andExpect(jsonPath("$.rule").value(Throughput.RULE))
			.andExpect(jsonPath("$.remaining").value(40))
			.andExpect(jsonPath("$.window.weeks").value(20))
			.andExpect(jsonPath("$.window.from").value(WEEK_ONE.toString()))
			.andExpect(jsonPath("$.window.to").value(asOf.toString()))
			.andExpect(jsonPath("$.window.completed").value(100))
			.andExpect(jsonPath("$.window.perWeek").value(5.0))
			.andExpect(jsonPath("$.window.best").value(5))
			.andExpect(jsonPath("$.window.worst").value(5))
			.andExpect(jsonPath("$.projection.meanWeeks").value(8.0))
			.andExpect(jsonPath("$.projection.p50Weeks").value(8))
			.andExpect(jsonPath("$.projection.p95Weeks").value(8))
			.andExpect(jsonPath("$.projection.p50Date").value(asOf.plusWeeks(8).toString()))
			.andExpect(jsonPath("$.projection.p95Date").value(asOf.plusWeeks(8).toString()))
			.andExpect(jsonPath("$.projection.sampleCount").value(10000));
	}

	/**
	 * Sixty-four bits, so a JSON number would arrive in a browser silently rounded — and
	 * a seed that is nearly right reproduces nothing. {@code jsonPath().value} re-reads a
	 * document as the expected type, so {@code isString} is what actually pins it.
	 */
	@Test
	void theSeedGoesOutAsAString() throws Exception {
		steady(5, 20);
		remaining(40);

		read(this.ada, WEEK_ONE.plusWeeks(19)).andExpect(status().isOk())
			.andExpect(jsonPath("$.projection.seed").isString());
	}

	/**
	 * The same question twice is the same answer, or a reader refreshing sees the plan
	 * move.
	 */
	@Test
	void theSameQuestionGivesTheSameSeed() throws Exception {
		steady(5, 20);
		remaining(40);
		LocalDate asOf = WEEK_ONE.plusWeeks(19);
		String seed = seedFrom(read(this.ada, asOf));

		read(this.ada, asOf).andExpect(jsonPath("$.projection.seed").value(seed));
	}

	/** The unconditional one, and it is the sentence `roadmap.md` gets wrong. */
	@Test
	void everyAnswerSaysItCannotSeeUnlistedWork() throws Exception {
		steady(5, 20);
		remaining(40);

		read(this.ada, WEEK_ONE.plusWeeks(19)).andExpect(status().isOk())
			.andExpect(jsonPath("$.limitations", hasItem("throughput_excludes_unlisted_work")));
	}

	/** Enough to project from, not enough to leave unremarked. */
	@Test
	void aQuarterOfHistoryIsProjectedAndFlagged() throws Exception {
		steady(5, Throughput.WORTH_SHOWING);
		remaining(10);

		read(this.ada, WEEK_ONE.plusWeeks(Throughput.WORTH_SHOWING - 1L)).andExpect(status().isOk())
			.andExpect(jsonPath("$.projection.p50Weeks").value(2))
			.andExpect(jsonPath("$.limitations",
					containsInAnyOrder("throughput_excludes_unlisted_work", "throughput_window_is_short")));
	}

	@Test
	void aYearOfHistoryNeedsNoWarningBesideIt() throws Exception {
		steady(5, Throughput.WORTH_TRUSTING);
		remaining(10);

		read(this.ada, WEEK_ONE.plusWeeks(Throughput.WORTH_TRUSTING - 1L)).andExpect(status().isOk())
			.andExpect(jsonPath("$.limitations", not(hasItem("throughput_window_is_short"))));
	}

	// The burn-up ---------------------------------------------------------------

	/**
	 * <strong>The picture is the same numbers as the sentence above it.</strong> Twenty
	 * weeks of five a week, forty left: the past climbs by five a week to a hundred, and
	 * the cone continues from a hundred to a hundred and forty in the eighth week — which
	 * is the same eight weeks {@code projection.p50Weeks} publishes, because the two are
	 * one forecast read twice rather than two forecasts.
	 */
	@Test
	void drawsWhatHasBeenDeliveredAndWhatTheHistorySaysIsLeft() throws Exception {
		steady(5, 20);
		remaining(40);
		LocalDate asOf = WEEK_ONE.plusWeeks(19);

		read(this.ada, asOf).andExpect(status().isOk())
			.andExpect(jsonPath("$.burnUp.delivered").value(100))
			.andExpect(jsonPath("$.burnUp.total").value(140))
			.andExpect(jsonPath("$.burnUp.past.length()").value(20))
			.andExpect(jsonPath("$.burnUp.past[0].week").value(WEEK_ONE.toString()))
			.andExpect(jsonPath("$.burnUp.past[0].delivered").value(5))
			.andExpect(jsonPath("$.burnUp.past[19].week").value(WEEK_ONE.plusWeeks(19).toString()))
			.andExpect(jsonPath("$.burnUp.past[19].delivered").value(100))
			.andExpect(jsonPath("$.burnUp.cone.length()").value(9))
			.andExpect(jsonPath("$.burnUp.cone[0].week").value(asOf.toString()))
			.andExpect(jsonPath("$.burnUp.cone[0].p10").value(100))
			.andExpect(jsonPath("$.burnUp.cone[0].p90").value(100))
			.andExpect(jsonPath("$.burnUp.cone[8].week").value(asOf.plusWeeks(8).toString()))
			.andExpect(jsonPath("$.burnUp.cone[8].p10").value(140))
			.andExpect(jsonPath("$.burnUp.cone[8].p90").value(140));
	}

	/**
	 * <strong>The cone carries what is already finished rather than starting again from
	 * nothing.</strong> A reader adding the past to the future themselves is a reader who
	 * might not, and the picture would have two lines that do not meet.
	 */
	@Test
	void theConeContinuesTheLineRatherThanRestartingIt() throws Exception {
		steady(5, 20);
		remaining(40);
		LocalDate asOf = WEEK_ONE.plusWeeks(19);

		read(this.ada, asOf).andExpect(status().isOk())
			// The last week delivered and the first week projected are the same figure.
			.andExpect(jsonPath("$.burnUp.past[19].delivered").value(100))
			.andExpect(jsonPath("$.burnUp.cone[0].p50").value(100));
	}

	/**
	 * <strong>A quiet fortnight is flat in the picture, which is the whole of decision 2
	 * arriving where somebody can see it.</strong> Ten in the first week and nothing for
	 * three is a line that climbs once and then holds — not three weeks nobody drew.
	 */
	@Test
	void aQuietFortnightIsAFlatStretchRatherThanAMissingWeek() throws Exception {
		for (int item = 0; item < 10; item++) {
			finished(WEEK_ONE);
		}
		remaining(5);

		read(this.ada, WEEK_ONE.plusWeeks(3)).andExpect(status().isOk())
			.andExpect(jsonPath("$.burnUp.past.length()").value(4))
			.andExpect(jsonPath("$.burnUp.past[0].delivered").value(10))
			.andExpect(jsonPath("$.burnUp.past[1].delivered").value(10))
			.andExpect(jsonPath("$.burnUp.past[3].delivered").value(10))
			.andExpect(jsonPath("$.burnUp.past[3].week").value(WEEK_ONE.plusWeeks(3).toString()));
	}

	/**
	 * <strong>Its past and no cone, saying which</strong> — M9's three states arriving
	 * here unchanged. The cone is absent exactly when the projection is, so a picture can
	 * never show a future the sentence beside it declines to state.
	 */
	@Test
	void tooLittleHistoryDrawsItsPastAndNoCone() throws Exception {
		steady(5, Throughput.WORTH_SHOWING - 1);
		remaining(40);

		read(this.ada, WEEK_ONE.plusWeeks(Throughput.WORTH_SHOWING - 2L)).andExpect(status().isOk())
			.andExpect(jsonPath("$.burnUp.past.length()").value(Throughput.WORTH_SHOWING - 1))
			.andExpect(jsonPath("$.burnUp.cone").value(nullValue()))
			.andExpect(jsonPath("$.limitations", hasItem("throughput_history_too_short")));
	}

	/**
	 * And a finished plan draws its own past, arriving at its total with nothing left.
	 */
	@Test
	void aFinishedPlanDrawsItsPastAndNoCone() throws Exception {
		steady(5, 20);

		read(this.ada, WEEK_ONE.plusWeeks(19)).andExpect(status().isOk())
			.andExpect(jsonPath("$.burnUp.delivered").value(100))
			.andExpect(jsonPath("$.burnUp.total").value(100))
			.andExpect(jsonPath("$.burnUp.cone").value(nullValue()));
	}

	/**
	 * <strong>And a plan whose rate does not clear its backlog draws no cone
	 * either.</strong> Its percentiles are censored at the horizon and withheld for that
	 * reason; a route that climbs for ten years and never arrives is that same censored
	 * answer drawn, and building one is where it would cost most.
	 */
	@Test
	void aRateTooSlowToFinishDrawsNoCone() throws Exception {
		finished(WEEK_ONE);
		remaining(100);

		read(this.ada, WEEK_ONE.plusWeeks(Throughput.MOST_WEEKS - 1L)).andExpect(status().isOk())
			.andExpect(jsonPath("$.burnUp.cone").value(nullValue()))
			.andExpect(jsonPath("$.limitations", hasItem("throughput_beyond_horizon")));
	}

	/**
	 * A plan nobody has finished anything in has no picture at all, for the reason it has
	 * no window: an empty axis is a chart of nothing, and the limitation already says
	 * why.
	 */
	@Test
	void aPlanWithNoHistoryHasNothingToDraw() throws Exception {
		remaining(40);

		read(this.ada, WEEK_ONE).andExpect(status().isOk()).andExpect(jsonPath("$.burnUp").value(nullValue()));
	}

	// The three ways to have no projection --------------------------------------

	/**
	 * <strong>The window ships and the projection does not</strong>, which is M8's empty
	 * state in a second place: a reader gets what there is and a reason, rather than an
	 * absence.
	 */
	@Test
	void tooLittleHistoryIsAWindowAndNoForecast() throws Exception {
		steady(5, Throughput.WORTH_SHOWING - 1);
		remaining(40);

		read(this.ada, WEEK_ONE.plusWeeks(Throughput.WORTH_SHOWING - 2L)).andExpect(status().isOk())
			.andExpect(jsonPath("$.window.weeks").value(Throughput.WORTH_SHOWING - 1))
			.andExpect(jsonPath("$.projection").value(nullValue()))
			.andExpect(jsonPath("$.limitations", hasItem("throughput_history_too_short")));
	}

	/** No weeks at all, so no window either — the two are separately absent. */
	@Test
	void aPlanNobodyHasFinishedAnythingInHasNoWindow() throws Exception {
		remaining(40);

		read(this.ada, WEEK_ONE).andExpect(status().isOk())
			.andExpect(jsonPath("$.remaining").value(40))
			.andExpect(jsonPath("$.window").value(nullValue()))
			.andExpect(jsonPath("$.projection").value(nullValue()))
			.andExpect(jsonPath("$.limitations", hasItem("throughput_history_too_short")));
	}

	/** A plan with nothing left is not a forecast of no weeks. */
	@Test
	void aFinishedPlanSaysThereIsNothingLeft() throws Exception {
		steady(5, 20);

		read(this.ada, WEEK_ONE.plusWeeks(19)).andExpect(status().isOk())
			.andExpect(jsonPath("$.remaining").value(0))
			.andExpect(jsonPath("$.window.weeks").value(20))
			.andExpect(jsonPath("$.projection").value(nullValue()))
			.andExpect(jsonPath("$.limitations", hasItem("throughput_nothing_left")));
	}

	/**
	 * One completion in ten years against a backlog of a hundred: every percentile would
	 * stand at the horizon, and a censored number rendered as a date is read as a date.
	 * Withheld, with the reason.
	 */
	@Test
	void aRateTooSlowToFinishIsNotPublishedAsADate() throws Exception {
		finished(WEEK_ONE);
		remaining(100);

		read(this.ada, WEEK_ONE.plusWeeks(Throughput.MOST_WEEKS - 1L)).andExpect(status().isOk())
			.andExpect(jsonPath("$.window.weeks").value(Throughput.MOST_WEEKS))
			.andExpect(jsonPath("$.projection").value(nullValue()))
			.andExpect(jsonPath("$.limitations", hasItem("throughput_beyond_horizon")));
	}

	// Refusals ------------------------------------------------------------------

	/**
	 * Bucketing a completion into a week that has not happened would give a history whose
	 * last week is before its own last delivery, and every number read off it would be
	 * wrong in a way nobody could see.
	 */
	@Test
	void refusesADayBeforeSomeOfTheHistoryHappened() throws Exception {
		steady(5, 20);
		remaining(40);

		read(this.ada, WEEK_ONE).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("throughput_out_of_order"));
	}

	/**
	 * And it is the latest completion that decides, wherever it happens to sit in the
	 * answer. The query orders ascending, so this passes either way today — it is here
	 * because the check must not be the thing that breaks when somebody relaxes an `order
	 * by`.
	 */
	@Test
	void refusesOnTheLatestCompletionAndNotOnTheLastRow() throws Exception {
		finished(WEEK_ONE.plusWeeks(30));
		finished(WEEK_ONE);
		remaining(40);

		read(this.ada, WEEK_ONE.plusWeeks(19)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("throughput_out_of_order"));
	}

	@Test
	void anotherOrganisationsPlanIsNotThere() throws Exception {
		steady(5, 20);
		remaining(40);

		read(this.grace, WEEK_ONE.plusWeeks(19)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	@Test
	void isRefusedToSomebodyWhoNoLongerBelongs() throws Exception {
		String token = bearer(this.ada);
		this.memberships.delete(this.ada);

		this.mvc
			.perform(get("/api/projects/" + this.plan.getId() + "/throughput").param("asOf", WEEK_ONE.toString())
				.header(HttpHeaders.AUTHORIZATION, token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/projects/" + this.plan.getId() + "/throughput").param("asOf", WEEK_ONE.toString()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void requiresADayToAskAbout() throws Exception {
		this.mvc
			.perform(get("/api/projects/" + this.plan.getId() + "/throughput").header(HttpHeaders.AUTHORIZATION,
					bearer(this.ada)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.asOf.code").value("not_null"));
	}

	/**
	 * And there but unreadable, which is the same thing to a caller: the parameter this
	 * endpoint needs is not usable. Without a handler both of these arrive as Boot's
	 * default — a problem document with no code in it, which is indistinguishable from
	 * the server having fallen over.
	 */
	@Test
	void refusesADayItCannotRead() throws Exception {
		this.mvc
			.perform(get("/api/projects/" + this.plan.getId() + "/throughput").param("asOf", "the-fifth-of-never")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("validation_failed"))
			.andExpect(jsonPath("$.errors.asOf.code").value("invalid"));
	}

	// Fixtures -----------------------------------------------------------------

	private ResultActions read(Membership caller, LocalDate asOf) throws Exception {
		return this.mvc.perform(get("/api/projects/" + this.plan.getId() + "/throughput").param("asOf", asOf.toString())
			.header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private static String seedFrom(ResultActions answered) throws Exception {
		return com.jayway.jsonpath.JsonPath.read(answered.andReturn().getResponse().getContentAsString(),
				"$.projection.seed");
	}

	/** A team that finished the same number every week, starting in week one. */
	private void steady(int each, int weeks) {
		for (int week = 0; week < weeks; week++) {
			for (int item = 0; item < each; item++) {
				finished(WEEK_ONE.plusWeeks(week));
			}
		}
	}

	private void finished(LocalDate on) {
		WorkItem item = this.items.save(new WorkItem(this.plan, "Delivered " + on, null, CREATED_AT));
		item.recordProgress(WorkItemStatus.DONE, null, on, null);
		this.items.save(item);
	}

	private void remaining(int count) {
		for (int at = 0; at < count; at++) {
			this.items.save(new WorkItem(this.plan, "Still to do " + at, null, CREATED_AT));
		}
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
