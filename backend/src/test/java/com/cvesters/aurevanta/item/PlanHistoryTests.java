package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectRepository;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.tenant.TenantRepository;
import com.cvesters.aurevanta.user.User;
import com.cvesters.aurevanta.user.UserRepository;
import com.cvesters.aurevanta.user.UserRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * What a plan has finished, and what it has left.
 *
 * <p>
 * <strong>The two answers disagree about archived work, deliberately, and that is what
 * most of this class is about.</strong> A task that was delivered and later put away was
 * still delivered, so it belongs in the history — dropping it would make tidying up look
 * like a slowdown. A task put away before it was finished is not going to be delivered at
 * all, so it is not in the backlog. A single "ignore archived" rule is wrong in exactly
 * one of those two places and looks right in both, so each is asserted on its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PlanHistoryTests {

	private static final Instant CREATED_AT = Instant.parse("2026-03-02T08:00:00Z");

	private static final LocalDate BEGAN = LocalDate.parse("2026-03-02");

	@Autowired
	private WorkItemService service;

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

	private Membership ada;

	private Membership grace;

	private Project plan;

	private Project otherPlan;

	private Project theirPlan;

	@BeforeEach
	void seedTwoOrganisationsWithPlansInThem() {
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
		this.otherPlan = this.projects.save(new Project(acme, "Q4 platform work", null, CREATED_AT));
		this.theirPlan = this.projects.save(new Project(umbrella, "Their plan", null, CREATED_AT));
	}

	// What it finished ---------------------------------------------------------

	@Test
	void handsBackTheDayEachFinishedItemWasFinished() {
		finished("Second", BEGAN.plusDays(9));
		finished("First", BEGAN.plusDays(2));
		finished("Third", BEGAN.plusDays(20));

		assertThat(completions()).containsExactly(BEGAN.plusDays(2), BEGAN.plusDays(9), BEGAN.plusDays(20));
	}

	/** <strong>Tidying up is not a slowdown.</strong> */
	@Test
	void workFinishedAndLaterPutAwayIsStillInTheHistory() {
		WorkItem delivered = finished("Delivered then archived", BEGAN.plusDays(3));
		delivered.archive(CREATED_AT);
		this.items.save(delivered);

		assertThat(completions()).containsExactly(BEGAN.plusDays(3));
	}

	@Test
	void workStillUnderWayIsNotInTheHistory() {
		WorkItem running = item("Still going");
		running.recordProgress(WorkItemStatus.IN_PROGRESS, BEGAN, null, null);
		this.items.save(running);
		item("Not begun");

		assertThat(completions()).isEmpty();
	}

	/**
	 * Reachable only by writing the row outside the service, which is why the query says
	 * so rather than trusting the rule: a null day cannot be put in a week, and it would
	 * arrive as one in a list of days.
	 */
	@Test
	void finishedWorkWithNoDayOnItIsLeftOut() {
		WorkItem undated = item("Finished, nobody said when");
		undated.recordProgress(WorkItemStatus.DONE, null, null, null);
		this.items.save(undated);
		finished("Finished properly", BEGAN.plusDays(1));

		assertThat(completions()).containsExactly(BEGAN.plusDays(1));
	}

	@Test
	void anotherPlanInTheSameOrganisationIsADifferentHistory() {
		finished("Ours", BEGAN.plusDays(1));
		WorkItem theirs = this.items.save(new WorkItem(this.otherPlan, "Next quarter's", null, CREATED_AT));
		theirs.recordProgress(WorkItemStatus.DONE, null, BEGAN.plusDays(2), null);
		this.items.save(theirs);

		assertThat(completions()).containsExactly(BEGAN.plusDays(1));
	}

	@Test
	void aPlanNobodyHasFinishedAnythingInHasAnEmptyHistory() {
		item("Not begun");

		assertThat(completions()).isEmpty();
	}

	// What it has left ---------------------------------------------------------

	@Test
	void countsEverythingNotYetFinished() {
		item("Not begun");
		WorkItem running = item("Still going");
		running.recordProgress(WorkItemStatus.IN_PROGRESS, BEGAN, null, null);
		this.items.save(running);
		finished("Done", BEGAN.plusDays(1));

		assertThat(remaining()).isEqualTo(2);
	}

	/**
	 * <strong>Work put away before it was finished is not going to be delivered.</strong>
	 */
	@Test
	void workPutAwayBeforeItWasFinishedIsNotInTheBacklog() {
		item("Still to do");
		WorkItem shelved = item("Shelved");
		shelved.archive(CREATED_AT);
		this.items.save(shelved);

		assertThat(remaining()).isEqualTo(1);
	}

	/**
	 * <strong>The one place a throughput forecast is better informed than the
	 * engine.</strong> An unestimated item is a hole in a band and reports itself as a
	 * limitation; here it is an item like any other, because what is counted is work left
	 * rather than effort left — and nothing in either query has ever heard of an
	 * estimate.
	 */
	@Test
	void anUnestimatedItemCountsLikeAnyOther() {
		item("Nobody estimated this");
		item("Nobody estimated this either");

		assertThat(remaining()).isEqualTo(2);
	}

	@Test
	void anotherPlanInTheSameOrganisationIsADifferentBacklog() {
		item("Ours");
		this.items.save(new WorkItem(this.otherPlan, "Next quarter's", null, CREATED_AT));

		assertThat(remaining()).isEqualTo(1);
	}

	@Test
	void aPlanWithNothingLeftCountsNothing() {
		finished("Done", BEGAN.plusDays(1));

		assertThat(remaining()).isZero();
	}

	// Whose plan it is ---------------------------------------------------------

	@Test
	void anotherOrganisationsPlanIsNotThere() {
		UUID caller = this.ada.getUser().getId();
		UUID tenantId = this.ada.getTenant().getId();
		UUID hidden = this.theirPlan.getId();

		assertThatExceptionOfType(ProjectNotFoundException.class)
			.isThrownBy(() -> this.service.completionsIn(caller, tenantId, hidden));
		assertThatExceptionOfType(ProjectNotFoundException.class)
			.isThrownBy(() -> this.service.remainingIn(caller, tenantId, hidden));
	}

	@Test
	void isRefusedToSomebodyWhoNoLongerBelongs() {
		UUID stranger = this.grace.getUser().getId();
		UUID tenantId = this.ada.getTenant().getId();
		UUID planId = this.plan.getId();

		assertThatExceptionOfType(NotAMemberException.class)
			.isThrownBy(() -> this.service.completionsIn(stranger, tenantId, planId));
		assertThatExceptionOfType(NotAMemberException.class)
			.isThrownBy(() -> this.service.remainingIn(stranger, tenantId, planId));
	}

	// Fixtures -----------------------------------------------------------------

	private List<LocalDate> completions() {
		return this.service.completionsIn(this.ada.getUser().getId(), this.ada.getTenant().getId(), this.plan.getId());
	}

	private int remaining() {
		return this.service.remainingIn(this.ada.getUser().getId(), this.ada.getTenant().getId(), this.plan.getId());
	}

	private WorkItem item(String title) {
		return this.items.save(new WorkItem(this.plan, title, null, CREATED_AT));
	}

	private WorkItem finished(String title, LocalDate on) {
		WorkItem item = item(title);
		item.recordProgress(WorkItemStatus.DONE, null, on, new BigDecimal("4.00"));
		return this.items.save(item);
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
