package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProgressDateRequiredException;
import com.cvesters.aurevanta.problem.ProgressNotApplicableException;
import com.cvesters.aurevanta.problem.ProgressOutOfOrderException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.WorkItemNotFoundException;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.project.ProjectService;
import com.cvesters.aurevanta.user.User;

/**
 * The work inside a plan, and everything a member may do to it.
 *
 * <p>
 * Any member may do all of it, as with projects: roles govern administration only.
 * Nothing here deletes anything either — an item archives, and from step 3 it carries
 * estimates that calibration reads years later.
 *
 * <p>
 * <strong>A progress report is written twice, and the two writes are not the same
 * thing.</strong> The four columns on {@link WorkItem} hold the latest state, which is
 * what a screen draws and what a forecast reads; {@link WorkItemProgress} holds every
 * claim ever made, which is what calibration measures an estimate's date against. Both
 * happen in one transaction here, so the item's state and the last line of its history
 * cannot disagree — and the second is the reason the first may be written over safely.
 *
 * <p>
 * Two ways in, and the difference is deliberate. <strong>Creating and listing go through
 * the project</strong>, which is looked up by {@link ProjectService} and so re-checks the
 * caller's membership and the project's organisation in one place. <strong>Changing an
 * item that already exists names only the item</strong>, because from step 3 an estimate
 * and from step 5 a dependency address items directly, and a path that repeated the
 * project would be a second identifier the server had to check agreed with the first.
 */
@Service
public class WorkItemService {

	private final WorkItemRepository items;

	private final WorkItemProgressRepository reports;

	private final ProjectService projects;

	private final MembershipService memberships;

	private final Clock clock;

	WorkItemService(WorkItemRepository items, WorkItemProgressRepository reports, ProjectService projects,
			MembershipService memberships, Clock clock) {
		this.items = items;
		this.reports = reports;
		this.projects = projects;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Writes a piece of work down. The tenant comes off the project rather than off the
	 * caller's token, so the row's own copy of it cannot disagree with the plan it is in.
	 *
	 * <p>
	 * An archived project accepts items, deliberately: archiving says the plan is not
	 * being worked from, and refusing here would need a refusal nobody asked for to
	 * describe somebody tidying up an old plan they had just brought back.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional
	public WorkItem create(UUID callerId, UUID tenantId, UUID projectId, String title, String description) {
		// The entity rather than the read model: how much of the plan is estimated is not
		// a question worth two queries every time somebody types a task into it.
		Project project = this.projects.get(callerId, tenantId, projectId);
		return this.items.save(new WorkItem(project, title, description, Instant.now(this.clock)));
	}

	/**
	 * The work in one plan, the items in use or the ones put away.
	 *
	 * <p>
	 * The project is fetched rather than assumed, so a plan that does not exist answers
	 * {@code project_not_found} rather than with an empty list — "no such plan" and "a
	 * plan with nothing in it" are different answers, and only one of them is worth
	 * acting on.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<WorkItem> list(UUID callerId, UUID tenantId, UUID projectId, boolean archived) {
		this.projects.get(callerId, tenantId, projectId);
		return this.items.findAllInProject(tenantId, projectId, archived);
	}

	/**
	 * One item, if the caller may reach it.
	 *
	 * <p>
	 * Public because an estimate is <em>of</em> an item, and {@code estimate} asks this
	 * question rather than looking the row up itself: whether somebody may write against
	 * a piece of work is this package's rule, and a second copy of it would be a second
	 * chance for one of them to drift.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional(readOnly = true)
	public WorkItem get(UUID callerId, UUID tenantId, UUID itemId) {
		return item(callerId, tenantId, itemId);
	}

	/**
	 * Changes what one item is called and what is said about it.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional
	public WorkItem update(UUID callerId, UUID tenantId, UUID itemId, String title, String description) {
		WorkItem item = item(callerId, tenantId, itemId);
		item.describe(title, description);
		return item;
	}

	/**
	 * Records what has already happened to one item, so a forecast can leave it out
	 * rather than predict it again.
	 *
	 * <p>
	 * <strong>The request states the whole of it, and anything it says that its own
	 * status cannot hold is refused rather than dropped.</strong> Keeping the parts that
	 * fit and discarding the rest is what this did first, and it meant somebody could
	 * record four hours against work marked not started, watch the form close, and find
	 * the number gone with nothing said about it.
	 *
	 * <p>
	 * The other two checks make the dates evidence instead of decoration: a state that
	 * needs a date and did not get one is refused rather than stamped with the server's
	 * clock, and a completion before its start is refused because nothing downstream
	 * could tell that from a fact.
	 *
	 * <p>
	 * <strong>And the claim is appended as well as applied.</strong> Until {@code V16}
	 * this wrote over the last report with nothing recording who had made it or that it
	 * had ever said something else — so calibration's exclusion rule, which asks whether
	 * an estimate predates the start of the work, could be satisfied after the fact by
	 * editing the start.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 * @throws ProgressDateRequiredException if the state claimed has no date to support
	 * it
	 * @throws ProgressNotApplicableException if it carries what that state cannot hold
	 * @throws ProgressOutOfOrderException if the work finished before it began
	 */
	@Transactional
	public WorkItem recordProgress(UUID callerId, UUID tenantId, UUID itemId, WorkItemStatus status,
			LocalDate startedOn, LocalDate completedOn, BigDecimal actualEffortHours) {
		requireConsistent(status, startedOn, completedOn, actualEffortHours);
		// The reporter comes off the membership that just proved the caller belongs here,
		// which is where an estimate takes its estimator from and for the same reason:
		// the
		// row is already loaded, and a second lookup by the identifier in their token
		// could come back empty in a way this method cannot actually have.
		User reporter = this.memberships.requireMember(callerId, tenantId).getUser();
		WorkItem item = this.items.findInTenant(itemId, tenantId).orElseThrow(WorkItemNotFoundException::new);
		Instant reportedAt = Instant.now(this.clock);
		item.recordProgress(status, startedOn, completedOn, actualEffortHours);
		// Appended in the same transaction as the write above, so the item's latest state
		// and the last line of its history cannot come to disagree — and appended even
		// when nothing changed, because somebody confirming what the row already said is
		// still somebody saying it.
		this.reports
			.save(new WorkItemProgress(item, reporter, status, startedOn, completedOn, actualEffortHours, reportedAt));
		return item;
	}

	/**
	 * Everything anybody has ever claimed about one piece of work, newest first.
	 *
	 * <p>
	 * <strong>A log nothing reads is a log that quietly stops being written
	 * correctly</strong>, which is half of why this exists; the other half is that the
	 * person looking at a progress form is the one who can tell whether the last claim on
	 * it was theirs.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<WorkItemProgress> progressOf(UUID callerId, UUID tenantId, UUID itemId) {
		// Fetched rather than assumed, so an item that is not there answers
		// work_item_not_found rather than with an empty history — "no such work" and
		// "nobody has said anything about it" are different answers.
		item(callerId, tenantId, itemId);
		return this.reports.findForItem(tenantId, itemId);
	}

	/**
	 * The earliest day work on each item was ever claimed to have begun, across one
	 * organisation.
	 *
	 * <p>
	 * <strong>This is the boundary calibration scores against</strong>, and every part of
	 * it is chosen to run in the unflattering direction. An estimate written after work
	 * began is a report by somebody who could already see how the task was going, so the
	 * earlier the boundary, the fewer estimates count as forecasts — and the number that
	 * comes out is the one nobody can improve by editing a date.
	 *
	 * <p>
	 * <strong>Two sources, and the log wins wherever it has anything to say.</strong>
	 * {@link #recordProgress} writes the column and appends the same claim in one
	 * transaction, so since {@code V16} every value the column has ever held is in the
	 * log — the earliest claim is therefore the log's earliest, and the column can only
	 * repeat one of them. What the column is for is the rows older than the table:
	 * {@code V16} backfilled nothing on purpose, so an item reported on before it exists
	 * has a start date and no history, and that date is the only claim anybody ever made
	 * about it.
	 *
	 * <p>
	 * So this is a fallback rather than a rival, and it is written as one. Taking the
	 * earlier of the two instead would read as more careful and would be a branch nothing
	 * could reach — the column is never below the log's floor while one write does both.
	 */
	@Transactional(readOnly = true)
	public Map<UUID, LocalDate> earliestReportedStarts(UUID callerId, UUID tenantId) {
		this.memberships.requireMember(callerId, tenantId);
		Map<UUID, LocalDate> earliest = new HashMap<>();
		for (ReportedStart claimed : this.reports.earliestReportedStarts(tenantId)) {
			earliest.put(claimed.itemId(), claimed.startedOn());
		}
		for (ReportedStart current : this.items.currentStarts(tenantId)) {
			earliest.putIfAbsent(current.itemId(), current.startedOn());
		}
		return earliest;
	}

	/**
	 * Every way a claim can disagree with itself, checked before the item is looked up —
	 * as the estimate ordering is, and for the same reason: these are facts about the
	 * request alone, so answering them first tells a caller nothing about which items
	 * exist.
	 */
	private static void requireConsistent(WorkItemStatus status, LocalDate startedOn, LocalDate completedOn,
			BigDecimal actualEffortHours) {
		// Deliberately not a switch: one covering every constant of an enum still
		// compiles
		// to a default nothing can reach, and an unreachable branch is a hole in the
		// coverage gate that no test can close.
		if (status == WorkItemStatus.NOT_STARTED) {
			// Nothing has happened, so there is nothing to report about it.
			if (startedOn != null || completedOn != null || actualEffortHours != null) {
				throw new ProgressNotApplicableException();
			}
			return;
		}
		if (status == WorkItemStatus.IN_PROGRESS) {
			if (startedOn == null) {
				throw new ProgressDateRequiredException();
			}
			// Effort so far is a real thing to record; a completion date is not, because
			// this is the state for work that has not been finished.
			if (completedOn != null) {
				throw new ProgressNotApplicableException();
			}
			return;
		}
		// DONE, and the only state whose two dates have to agree with each other.
		if (completedOn == null) {
			throw new ProgressDateRequiredException();
		}
		// A start is optional even here — plenty of work is ticked off by somebody who
		// never marked it as begun — but where both are given they have to agree about
		// which came first.
		if (startedOn != null && completedOn.isBefore(startedOn)) {
			throw new ProgressOutOfOrderException();
		}
	}

	/**
	 * How much of this organisation's finished work says how long it took.
	 *
	 * <p>
	 * Two numbers rather than a list, because the only thing anybody does with them is
	 * subtract: work reported as done and never measured is why a calibration record is
	 * empty, and saying so with a count is the difference between a screen that explains
	 * itself and one that says "no data".
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public CompletedWork completedWork(UUID callerId, UUID tenantId) {
		this.memberships.requireMember(callerId, tenantId);
		return this.items.completedWork(tenantId, WorkItemStatus.DONE);
	}

	/**
	 * The day each of this plan's finished items was reported finished, oldest first.
	 *
	 * <p>
	 * What a throughput forecast is measured from, and it needs nothing anybody had to
	 * opt into: a completion date is required on everything marked done, so this exists
	 * in full for every plan. Compare {@code completedWork} above, which reads the
	 * organisation and finds most of its actuals missing.
	 *
	 * <p>
	 * The plan is fetched rather than assumed, so one that is not there answers
	 * {@code project_not_found} rather than with the history of nothing.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<LocalDate> completionsIn(UUID callerId, UUID tenantId, UUID projectId) {
		this.projects.get(callerId, tenantId, projectId);
		return this.items.completionsInProject(tenantId, projectId, WorkItemStatus.DONE);
	}

	/**
	 * How much of this plan is still to be delivered.
	 *
	 * <p>
	 * An {@code int} rather than the {@code long} the count arrives as, and
	 * {@link Math#toIntExact} rather than a cast: 500 items to a plan is the stated
	 * ceiling this work's arithmetic assumes, so a number that could not fit is a broken
	 * assumption and should say so rather than wrap silently into a backlog of minus two
	 * billion.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public int remainingIn(UUID callerId, UUID tenantId, UUID projectId) {
		this.projects.get(callerId, tenantId, projectId);
		return Math.toIntExact(this.items.countRemainingInProject(tenantId, projectId, WorkItemStatus.DONE));
	}

	/**
	 * Puts one item away, or brings it back.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 */
	@Transactional
	public WorkItem setArchived(UUID callerId, UUID tenantId, UUID itemId, boolean archived) {
		WorkItem item = item(callerId, tenantId, itemId);
		if (archived) {
			item.archive(Instant.now(this.clock));
		}
		else {
			item.unarchive();
		}
		return item;
	}

	/**
	 * The caller's standing re-read, and then the item found within their own
	 * organisation. Both halves matter: the first is what stops a token outliving a
	 * membership, and the second is what stops an identifier reaching outside the
	 * organisation that token names.
	 */
	private WorkItem item(UUID callerId, UUID tenantId, UUID itemId) {
		this.memberships.requireMember(callerId, tenantId);
		return this.items.findInTenant(itemId, tenantId).orElseThrow(WorkItemNotFoundException::new);
	}

}
