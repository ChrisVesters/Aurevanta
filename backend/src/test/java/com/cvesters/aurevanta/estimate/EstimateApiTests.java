package com.cvesters.aurevanta.estimate;

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
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The product's actual content: a range, from a named person, that is never overwritten.
 *
 * <p>
 * The tests worth reading twice are the ones about what <em>cannot</em> happen — that no
 * route rewrites a row, that a second estimate leaves the first readable, and that
 * removing somebody from an organisation does not take their estimates with them. Those
 * are the properties M8 depends on three years from now, and they are cheap to keep and
 * impossible to restore.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EstimateApiTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-13T08:00:00Z");

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
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AccessTokenService accessTokens;

	private Membership ada;

	private Membership bob;

	private Membership grace;

	private Project acmePlan;

	private WorkItem migration;

	@BeforeEach
	void seedAPlanWithWorkInIt() {
		this.estimates.deleteAll();
		this.items.deleteAll();
		this.projects.deleteAll();
		this.memberships.deleteAll();
		this.tenants.deleteAll();
		this.users.deleteAll();

		Tenant acme = this.tenants.save(new Tenant("Acme Planning Co", "acme-planning-co", CREATED_AT));
		Tenant umbrella = this.tenants.save(new Tenant("Umbrella", "umbrella", CREATED_AT));
		this.ada = member(user("ada@acme.test", "Ada"), acme, UserRole.OWNER);
		this.bob = member(user("bob@acme.test", "Bob"), acme, UserRole.MEMBER);
		this.grace = member(user("grace@umbrella.test", "Grace"), umbrella, UserRole.OWNER);
		this.acmePlan = this.projects.save(new Project(acme, "Q3 platform work", null, CREATED_AT));
		this.migration = this.items.save(new WorkItem(this.acmePlan, "Migrate the auth service", null, CREATED_AT));
	}

	// Recording one -----------------------------------------------------------

	@Test
	void aMemberCanEstimateAPieceOfWork() throws Exception {
		estimate(this.bob, this.migration, "3", "5", "12").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.itemId").value(this.migration.getId().toString()))
			.andExpect(jsonPath("$.estimatorId").value(this.bob.getUser().getId().toString()))
			// Whose range it is has to be legible, because the point of keeping several
			// is
			// that people disagree.
			.andExpect(jsonPath("$.estimatorName").value("Bob"))
			.andExpect(jsonPath("$.p10Hours").value(3.0))
			.andExpect(jsonPath("$.p50Hours").value(5.0))
			.andExpect(jsonPath("$.p90Hours").value(12.0))
			.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	/**
	 * <strong>An estimate says what is odd about it, and is stored anyway.</strong> The
	 * flags are advice — a tight band is sometimes exactly right — so nothing here is a
	 * refusal, and the row is written whatever they say. 5/10/40 implies a middle of 14.1
	 * against a stated 10, which is far enough to mention.
	 */
	@Test
	void anEstimateCarriesWhatIsWorthQuestioningAboutIt() throws Exception {
		estimate(this.ada, this.migration, "5", "10", "40").andExpect(status().isCreated())
			.andExpect(jsonPath("$.consistency").value(closeTo(0.707, 0.001)))
			.andExpect(jsonPath("$.inconsistent").value(true))
			.andExpect(jsonPath("$.overconfident").value(false));
	}

	/**
	 * <strong>And the estimate this milestone is named after says nothing is wrong with
	 * it.</strong> That is the measurement `m5-plan.md` opens with, asserted at the seam
	 * somebody would actually meet it: 3/5/8 is coherent garbage, and a screen reading
	 * these flags will show no warning at all. The question order is the defence; these
	 * two are a backstop.
	 */
	@Test
	void theCanonicalGarbageIsReportedAsPerfectlyFine() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "8").andExpect(status().isCreated())
			.andExpect(jsonPath("$.consistency").value(closeTo(1.021, 0.001)))
			.andExpect(jsonPath("$.inconsistent").value(false))
			.andExpect(jsonPath("$.overconfident").value(false));
	}

	/** A band too tight to have been thought about, flagged on the way back out. */
	@Test
	void aTightBandIsReportedOnTheCurrentEstimatesToo() throws Exception {
		estimate(this.ada, this.migration, "6", "10", "14").andExpect(status().isCreated());

		current(this.ada).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].overconfident").value(true))
			.andExpect(jsonPath("$[0].inconsistent").value(false));
	}

	/**
	 * Fractions of an hour are ordinary; the column keeps two places and so does the API.
	 */
	@Test
	void keepsTheFractionsOfAnHourItWasGiven() throws Exception {
		estimate(this.ada, this.migration, "0.25", "0.5", "1.75").andExpect(status().isCreated())
			.andExpect(jsonPath("$.p10Hours").value(0.25))
			.andExpect(jsonPath("$.p90Hours").value(1.75));
	}

	/**
	 * <strong>The property this whole step exists for.</strong> A second estimate is a
	 * second row: the first is still there, still readable, still what that person said
	 * at the time — which is the only question M8 can ask.
	 */
	@Test
	void revisingAnEstimateLeavesTheFirstOneReadable() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());

		estimate(this.ada, this.migration, "8", "13", "30").andExpect(status().isCreated());

		assertThat(this.estimates.findAll()).hasSize(2)
			.extracting((estimate) -> estimate.getP50Hours().stripTrailingZeros().toPlainString())
			.containsExactlyInAnyOrder("5", "13");
	}

	/** And the newer one is the one a reader is shown. */
	@Test
	void theCurrentEstimateIsTheLatestOne() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());
		estimate(this.ada, this.migration, "8", "13", "30").andExpect(status().isCreated());

		current(this.ada).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].p50Hours").value(13.0));
	}

	/**
	 * There is no route that rewrites a row, and that absence is the feature rather than
	 * an omission — so it is worth a test that fails if somebody adds one.
	 */
	@Test
	void thereIsNoRouteThatChangesAnEstimate() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());

		this.mvc
			.perform(patch("/api/items/" + this.migration.getId() + "/estimates")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"p10Hours\":1,\"p50Hours\":2,\"p90Hours\":3}"))
			.andExpect(status().isMethodNotAllowed());
	}

	/**
	 * Decision 2 working: two people may hold a current estimate on one item at once, and
	 * their disagreement is signal M3 will read rather than a conflict to refuse here.
	 */
	@Test
	void twoEstimatorsHoldCurrentEstimatesOnOneItemAtOnce() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());

		estimate(this.bob, this.migration, "10", "20", "40").andExpect(status().isCreated());

		current(this.ada).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[*].estimatorName").value(org.hamcrest.Matchers.containsInAnyOrder("Ada", "Bob")));
	}

	/**
	 * The estimator is a user and not a membership, so leaving an organisation does not
	 * take a person's estimates with them. M1 made removal delete the membership and
	 * never the identity; this is the half of that decision the schema had to keep.
	 */
	@Test
	void anEstimateSurvivesItsEstimatorLeavingTheOrganisation() throws Exception {
		estimate(this.bob, this.migration, "3", "5", "12").andExpect(status().isCreated());

		this.memberships.delete(this.bob);

		assertThat(this.estimates.findAll()).hasSize(1);
		current(this.ada).andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].estimatorName").value("Bob"));
	}

	// Refusing one ------------------------------------------------------------

	/**
	 * A band whose ends are the wrong way round is a typo, not a pessimistic estimate —
	 * and fitting a distribution to it would turn nonsense into a confident number.
	 */
	@Test
	void refusesAP50BelowTheP10() throws Exception {
		estimate(this.ada, this.migration, "5", "3", "12").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("estimate_out_of_order"));

		assertThat(this.estimates.findAll()).isEmpty();
	}

	@Test
	void refusesAP90BelowTheP50() throws Exception {
		estimate(this.ada, this.migration, "3", "12", "5").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("estimate_out_of_order"));
	}

	/**
	 * Equal is not out of order: somebody certain of a task says so by saying it twice.
	 */
	@Test
	void acceptsThreeNumbersThatAreAllTheSame() throws Exception {
		estimate(this.ada, this.migration, "4", "4", "4").andExpect(status().isCreated());
	}

	@Test
	void refusesAnEstimateOfNothing() throws Exception {
		estimate(this.ada, this.migration, "0", "5", "12").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.p10Hours.code").value("positive"));
	}

	@Test
	void refusesANegativeEstimate() throws Exception {
		estimate(this.ada, this.migration, "-1", "5", "12").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.p10Hours.code").value("positive"));
	}

	/**
	 * Finer than the column can hold is refused rather than rounded. Rounded, 0.005 hours
	 * would land as nothing at all — breaking the rule that had just let it in, and doing
	 * it silently after the check that enforces the rule.
	 */
	@Test
	void refusesMorePrecisionThanTheColumnKeeps() throws Exception {
		estimate(this.ada, this.migration, "0.005", "5", "12").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.p10Hours.code").value("digits"));

		assertThat(this.estimates.findAll()).isEmpty();
	}

	@Test
	void refusesARequestMissingOneOfTheThree() throws Exception {
		this.mvc
			.perform(post("/api/items/" + this.migration.getId() + "/estimates")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"p10Hours\":3,\"p90Hours\":12}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.p50Hours.code").value("not_null"));
	}

	/**
	 * The ordering is checked before the item is looked up, so a caller who sends
	 * nonsense learns nothing about which items exist by being told about it.
	 */
	@Test
	void refusesAnOutOfOrderEstimateWithoutSayingWhetherTheItemExists() throws Exception {
		this.mvc
			.perform(post("/api/items/" + UUID.randomUUID() + "/estimates")
				.header(HttpHeaders.AUTHORIZATION, bearer(this.ada))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"p10Hours\":5,\"p50Hours\":3,\"p90Hours\":12}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("estimate_out_of_order"));
	}

	// Whose work it is --------------------------------------------------------

	@Test
	void cannotEstimateWorkInAnotherOrganisation() throws Exception {
		Tenant umbrella = this.grace.getTenant();
		Project theirPlan = this.projects.save(new Project(umbrella, "Their plan", null, CREATED_AT));
		WorkItem theirWork = this.items.save(new WorkItem(theirPlan, "Their work", null, CREATED_AT));

		estimate(this.ada, theirWork, "3", "5", "12").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("work_item_not_found"));

		assertThat(this.estimates.findAll()).isEmpty();
	}

	@Test
	void cannotReadTheEstimatesOfAnotherOrganisationsPlan() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());

		this.mvc
			.perform(get("/api/projects/" + this.acmePlan.getId() + "/estimates").header(HttpHeaders.AUTHORIZATION,
					bearer(this.grace)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("project_not_found"));
	}

	@Test
	void requiresAToken() throws Exception {
		this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/estimates"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void refusesSomebodyRemovedFromTheOrganisationSinceTheirTokenWasIssued() throws Exception {
		String stale = bearer(this.bob);
		this.memberships.delete(this.bob);

		this.mvc
			.perform(
					post("/api/items/" + this.migration.getId() + "/estimates").header(HttpHeaders.AUTHORIZATION, stale)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"p10Hours\":3,\"p50Hours\":5,\"p90Hours\":12}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("not_a_member"));

		assertThat(this.estimates.findAll()).isEmpty();
	}

	// Coverage ----------------------------------------------------------------

	/**
	 * Decision 5's half of the bargain: a plan that is partly estimated is the ordinary
	 * case, and what M2 owes M3 is a number saying how much of it was left out.
	 */
	@Test
	void aProjectSaysHowMuchOfItIsEstimated() throws Exception {
		this.items.save(new WorkItem(this.acmePlan, "Write the runbook", null, CREATED_AT));
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());

		project().andExpect(jsonPath("$.itemCount").value(2)).andExpect(jsonPath("$.estimatedItemCount").value(1));
	}

	/** Several people estimating one item is one item covered, not two. */
	@Test
	void countsAnItemOnceHoweverManyPeopleEstimatedIt() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());
		estimate(this.bob, this.migration, "10", "20", "40").andExpect(status().isCreated());

		project().andExpect(jsonPath("$.itemCount").value(1)).andExpect(jsonPath("$.estimatedItemCount").value(1));
	}

	/** What somebody put away is not work the forecast is missing. */
	@Test
	void coverageIgnoresArchivedItems() throws Exception {
		WorkItem dropped = this.items.save(new WorkItem(this.acmePlan, "Something we dropped", null, CREATED_AT));
		estimate(this.ada, dropped, "3", "5", "12").andExpect(status().isCreated());
		dropped.archive(CREATED_AT);
		this.items.save(dropped);

		project().andExpect(jsonPath("$.itemCount").value(1)).andExpect(jsonPath("$.estimatedItemCount").value(0));
		// And the plan's estimates say the same thing, so a screen cannot show a range
		// for
		// work its own coverage says is not there.
		current(this.ada).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void aPlanWithNothingInItCoversNothing() throws Exception {
		Project empty = this.projects.save(new Project(this.ada.getTenant(), "Nothing yet", null, CREATED_AT));

		this.mvc.perform(get("/api/projects/" + empty.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$.itemCount").value(0))
			.andExpect(jsonPath("$.estimatedItemCount").value(0));
	}

	/** The listing carries it too, so coverage is visible without opening a plan. */
	@Test
	void theListOfPlansCarriesCoverageAsWell() throws Exception {
		estimate(this.ada, this.migration, "3", "5", "12").andExpect(status().isCreated());

		this.mvc.perform(get("/api/projects").header(HttpHeaders.AUTHORIZATION, bearer(this.ada)))
			.andExpect(jsonPath("$[0].itemCount").value(1))
			.andExpect(jsonPath("$[0].estimatedItemCount").value(1));
	}

	// Fixtures ----------------------------------------------------------------

	private ResultActions estimate(Membership caller, WorkItem item, String p10, String p50, String p90)
			throws Exception {
		RecordEstimateRequest request = new RecordEstimateRequest(new BigDecimal(p10), new BigDecimal(p50),
				new BigDecimal(p90));
		return this.mvc
			.perform(post("/api/items/" + item.getId() + "/estimates").header(HttpHeaders.AUTHORIZATION, bearer(caller))
				.contentType(MediaType.APPLICATION_JSON)
				.content(this.json.writeValueAsString(request)));
	}

	private ResultActions current(Membership caller) throws Exception {
		return this.mvc.perform(get("/api/projects/" + this.acmePlan.getId() + "/estimates")
			.header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private ResultActions project() throws Exception {
		return this.mvc
			.perform(get("/api/projects/" + this.acmePlan.getId()).header(HttpHeaders.AUTHORIZATION, bearer(this.ada)));
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
