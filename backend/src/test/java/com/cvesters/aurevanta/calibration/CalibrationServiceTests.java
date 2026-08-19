package com.cvesters.aurevanta.calibration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.estimate.Elicitation;
import com.cvesters.aurevanta.estimate.Estimate;
import com.cvesters.aurevanta.estimate.EstimateRepository;
import com.cvesters.aurevanta.forecast.model.Calibration;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemProgress;
import com.cvesters.aurevanta.item.WorkItemProgressRepository;
import com.cvesters.aurevanta.item.WorkItemRepository;
import com.cvesters.aurevanta.item.WorkItemStatus;
import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.membership.MembershipRepository;
import com.cvesters.aurevanta.problem.NotAMemberException;
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
 * Which estimates were forecasts, which were reports, and which cannot be told apart.
 *
 * <p>
 * <strong>The case worth reading first is
 * {@link #anEstimateWrittenAfterTheWorkBeganIsNotScoredAsAForecast}.</strong> Everything
 * else here supports one property: the headline must be improvable only by estimating
 * better. Writing an estimate once the work is visibly running, moving the start date
 * afterwards, and archiving a task that went badly are the three ways somebody could
 * otherwise make the number kinder, and each has a case below.
 *
 * <p>
 * The ranges are all 10 to 40 hours, so an outcome of 20 is a hit and one of 100 is a
 * miss — the arithmetic is {@code CalibrationTests}' and is not re-examined here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CalibrationServiceTests {

	private static final Instant CREATED_AT = Instant.parse("2026-06-01T08:00:00Z");

	private static final LocalDate BEGAN = LocalDate.parse("2026-07-10");

	private static final LocalDate FINISHED = LocalDate.parse("2026-07-20");

	/** Comfortably before the work began, so it is nobody's idea of a report. */
	private static final Instant FORESEEN = Instant.parse("2026-07-01T09:00:00Z");

	/** During the start day itself, which decision 1 refuses to read as a forecast. */
	private static final Instant ON_THE_DAY = Instant.parse("2026-07-10T14:00:00Z");

	/** Once the work was visibly running. */
	private static final Instant IN_HINDSIGHT = Instant.parse("2026-07-18T09:00:00Z");

	private static final BigDecimal P10 = new BigDecimal("10.00");

	private static final BigDecimal P90 = new BigDecimal("40.00");

	private static final BigDecimal HIT = new BigDecimal("20.00");

	private static final BigDecimal MISS = new BigDecimal("100.00");

	@Autowired
	private CalibrationService calibration;

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

	private Membership ada;

	private Membership linus;

	private Membership grace;

	private Tenant acme;

	private Project plan;

	@BeforeEach
	void seedAnOrganisationWithAPlanInIt() {
		this.estimates.deleteAll();
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
		this.plan = this.projects.save(new Project(this.acme, "Q3 platform work", null, CREATED_AT));
	}

	// The boundary -------------------------------------------------------------

	/**
	 * <strong>The property this whole design rests on.</strong> Both estimates below are
	 * the same three numbers; only when they were written differs, and one of them was
	 * written by somebody who could already see the task was running long. Fold them
	 * together and the headline improves for a reason that has nothing to do with
	 * estimating.
	 */
	@Test
	void anEstimateWrittenAfterTheWorkBeganIsNotScoredAsAForecast() {
		WorkItem missed = finished("Migrate the auth service", MISS);
		WorkItem sawItComing = finished("Write the runbook", MISS);
		estimated(missed, this.ada, FORESEEN);
		// The same band, written once the work had visibly overrun — a report, and it
		// happens to be a miss too, so the buckets differ by *when* and by nothing else.
		estimated(sawItComing, this.ada, IN_HINDSIGHT);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isEqualTo(1);
		assertThat(record.reports().estimates()).isEqualTo(1);
		assertThat(record.unbounded().estimates()).isZero();
	}

	/** And the buckets score independently, so hindsight cannot lift the headline. */
	@Test
	void hindsightIsScoredWhereItCannotFlatterTheHeadline() {
		estimated(finished("Missed it", MISS), this.ada, FORESEEN);
		estimated(finished("Knew by then", HIT), this.ada, IN_HINDSIGHT);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().hitRate().rate()).isZero();
		assertThat(record.reports().hitRate().rate()).isEqualTo(1.0);
	}

	/**
	 * Decision 1's price, and the reason it is counted. An estimate written at two in the
	 * afternoon of the day work began cannot be told from one written that morning before
	 * anybody started, because the start is a day and the estimate is a moment.
	 */
	@Test
	void anEstimateWrittenOnTheStartDayIsAReportAndTheCostIsCounted() {
		estimated(finished("Migrate the auth service", HIT), this.ada, ON_THE_DAY);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isZero();
		assertThat(record.reports().estimates()).isEqualTo(1);
		assertThat(record.coverage().movedByTheStartDay()).isEqualTo(1);
	}

	/**
	 * A report from long afterwards is not the start day and is not counted as its cost.
	 */
	@Test
	void aReportFromLongAfterwardsIsNotBlamedOnTheStartDay() {
		estimated(finished("Migrate the auth service", HIT), this.ada, IN_HINDSIGHT);

		assertThat(record().coverage().movedByTheStartDay()).isZero();
	}

	/**
	 * <strong>What step 1's log is for.</strong> Moving a start date forward would turn a
	 * report into a forecast, so the boundary reads the first day anybody claimed rather
	 * than the one the row currently holds.
	 */
	@Test
	void aStartDateMovedAfterwardsDoesNotTurnAReportIntoAForecast() {
		WorkItem item = finished("Migrate the auth service", MISS);
		reported(item, this.ada, BEGAN);
		// "Actually we started it a fortnight later", filed after the estimate was
		// written.
		LocalDate later = BEGAN.plusDays(14);
		item.recordProgress(WorkItemStatus.DONE, later, FINISHED, MISS);
		this.items.save(item);
		reported(item, this.ada, later);
		estimated(item, this.ada, IN_HINDSIGHT);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isZero();
		assertThat(record.reports().estimates()).isEqualTo(1);
	}

	/**
	 * Nobody said when it began, so nothing about it can be told from a report. Named
	 * rather than dropped: {@code DONE} needs no start date, so excluding these would
	 * throw away most of what a real organisation holds.
	 */
	@Test
	void workNobodyReportedAStartForIsScoredWhereItCannotBeMistakenForAForecast() {
		WorkItem item = this.items.save(new WorkItem(this.plan, "Ticked off in one go", null, CREATED_AT));
		item.recordProgress(WorkItemStatus.DONE, null, FINISHED, HIT);
		this.items.save(item);
		estimated(item, this.ada, FORESEEN);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isZero();
		assertThat(record.reports().estimates()).isZero();
		assertThat(record.unbounded().estimates()).isEqualTo(1);
	}

	// Which row is scored ------------------------------------------------------

	/**
	 * A revision written while the work was still ahead of them is a better forecast and
	 * the one they stand behind — not the first, and not whatever they said afterwards.
	 */
	@Test
	void theRowScoredIsTheLastThingSaidBeforeTheWorkBegan() {
		WorkItem item = finished("Migrate the auth service", HIT);
		// Wildly wrong, then corrected, then revised again once it was running.
		this.estimates.save(estimate(item, this.ada, new BigDecimal("1.00"), new BigDecimal("2.00"), FORESEEN));
		estimated(item, this.ada, FORESEEN.plusSeconds(3600));
		this.estimates.save(estimate(item, this.ada, new BigDecimal("90.00"), new BigDecimal("110.00"), IN_HINDSIGHT));

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isEqualTo(1);
		// The 10–40 band contained 20; the 1–2 band would have missed and the 90–110
		// band,
		// which is the newest, would have missed the other way.
		assertThat(record.forecasts().hits()).isEqualTo(1);
		assertThat(record.reports().estimates()).isZero();
	}

	/**
	 * Calibration is a property of people, so one task two people estimated is two
	 * answers.
	 */
	@Test
	void twoEstimatorsOnOneTaskAreTwoRowsAndNotOne() {
		WorkItem item = finished("Migrate the auth service", HIT);
		estimated(item, this.ada, FORESEEN);
		this.estimates.save(estimate(item, this.linus, new BigDecimal("60.00"), new BigDecimal("80.00"), FORESEEN));

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isEqualTo(2);
		assertThat(record.forecasts().hits()).isEqualTo(1);
		assertThat(record.coverage().scoredItems()).isEqualTo(1);
	}

	// What is not scored -------------------------------------------------------

	/**
	 * Nothing to have been right or wrong about, and it is the commonest gap of the two.
	 */
	@Test
	void finishedWorkNobodyMeasuredIsScoredNowhereAndCounted() {
		WorkItem unmeasured = this.items.save(new WorkItem(this.plan, "Nobody timed it", null, CREATED_AT));
		unmeasured.recordProgress(WorkItemStatus.DONE, BEGAN, FINISHED, null);
		this.items.save(unmeasured);
		estimated(unmeasured, this.ada, FORESEEN);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isZero();
		assertThat(record.coverage().completedItems()).isEqualTo(1);
		assertThat(record.coverage().withActual()).isZero();
		assertThat(record.coverage().withEstimate()).isEqualTo(1);
		assertThat(record.coverage().scoredItems()).isZero();
	}

	@Test
	void finishedWorkNobodyEstimatedIsCountedTheOtherWay() {
		finished("Nobody predicted it", HIT);

		OrganisationCalibration record = record();

		assertThat(record.coverage().completedItems()).isEqualTo(1);
		assertThat(record.coverage().withActual()).isEqualTo(1);
		assertThat(record.coverage().withEstimate()).isZero();
		assertThat(record.coverage().scoredItems()).isZero();
	}

	/**
	 * Unfinished work is a prediction that has not come due, not a prediction that
	 * failed.
	 */
	@Test
	void workStillUnderWayIsNotScoredAtAll() {
		WorkItem running = this.items.save(new WorkItem(this.plan, "Still going", null, CREATED_AT));
		// Effort so far is a real thing to record and is emphatically not an actual:
		// scoring
		// a partial as a final outcome would put every unfinished task below its own P10.
		running.recordProgress(WorkItemStatus.IN_PROGRESS, BEGAN, null, new BigDecimal("4.00"));
		this.items.save(running);
		estimated(running, this.ada, FORESEEN);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isZero();
		assertThat(record.coverage().completedItems()).isZero();
	}

	/** <strong>Archiving is not a way to lose a miss.</strong> */
	@Test
	void workPutAwaySinceIsStillScored() {
		WorkItem item = finished("Migrate the auth service", MISS);
		item.archive(CREATED_AT);
		this.items.save(item);
		estimated(item, this.ada, FORESEEN);

		OrganisationCalibration record = record();

		assertThat(record.forecasts().estimates()).isEqualTo(1);
		assertThat(record.forecasts().hits()).isZero();
	}

	// Whose record it is -------------------------------------------------------

	/**
	 * <strong>The tenant boundary is absolute, and this corrects a comment in
	 * {@code V9}</strong>, which says calibration calibrates across everything one person
	 * ever estimated in whichever organisation. A consultant with two clients has two
	 * records.
	 */
	@Test
	void anotherOrganisationsEvidenceIsInvisible() {
		estimated(finished("Migrate the auth service", MISS), this.ada, FORESEEN);

		OrganisationCalibration theirs = this.calibration.recordFor(this.grace.getUser().getId(),
				this.grace.getTenant().getId());

		assertThat(theirs.forecasts().estimates()).isZero();
		assertThat(theirs.coverage().completedItems()).isZero();
	}

	/**
	 * An estimate outlives the estimator's membership —
	 * {@code estimates.estimator_user_id} points at the account and never cascades — so
	 * an organisation's history of what it predicted does not change because somebody
	 * moved on.
	 */
	@Test
	void somebodyWhoHasLeftStillHasARecordHere() {
		estimated(finished("Migrate the auth service", HIT), this.linus, FORESEEN);
		this.memberships.delete(this.linus);

		assertThat(record().forecasts().estimates()).isEqualTo(1);
	}

	@Test
	void isRefusedToSomebodyWhoNoLongerBelongs() {
		UUID stranger = this.grace.getUser().getId();
		UUID tenantId = this.acme.getId();

		assertThatExceptionOfType(NotAMemberException.class)
			.isThrownBy(() -> this.calibration.recordFor(stranger, tenantId));
	}

	@Test
	void anOrganisationThatHasFinishedNothingSaysSoRatherThanScoringZero() {
		OrganisationCalibration record = record();

		assertThat(record.forecasts().hitRate().measured()).isFalse();
		assertThat(record.coverage()).isEqualTo(new CalibrationCoverage(0, 0, 0, 0, 0));
	}

	// Fixtures -----------------------------------------------------------------

	private OrganisationCalibration record() {
		return this.calibration.recordFor(this.ada.getUser().getId(), this.acme.getId());
	}

	/** A task reported as done, with a start claimed and an outcome measured. */
	private WorkItem finished(String title, BigDecimal actualHours) {
		WorkItem item = this.items.save(new WorkItem(this.plan, title, null, CREATED_AT));
		item.recordProgress(WorkItemStatus.DONE, BEGAN, FINISHED, actualHours);
		this.items.save(item);
		reported(item, this.ada, BEGAN);
		return item;
	}

	private void reported(WorkItem item, Membership by, LocalDate startedOn) {
		this.reports.save(new WorkItemProgress(item, by.getUser(), WorkItemStatus.DONE, startedOn, FINISHED,
				item.getActualEffortHours(), CREATED_AT));
	}

	private void estimated(WorkItem item, Membership by, Instant at) {
		this.estimates.save(estimate(item, by, P10, P90, at));
	}

	private Estimate estimate(WorkItem item, Membership by, BigDecimal p10, BigDecimal p90, Instant at) {
		BigDecimal middle = p10.add(p90).divide(new BigDecimal("2"));
		return new Estimate(item, by.getUser(), p10, middle, p90, Elicitation.SURPRISE_FRAMED, at);
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
