package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.forecast.model.EstimateQuality;
import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
import com.cvesters.aurevanta.item.WorkItemStatus;
import com.cvesters.aurevanta.membership.MembershipService;
import com.cvesters.aurevanta.problem.EstimateOutOfOrderException;
import com.cvesters.aurevanta.problem.NotAMemberException;
import com.cvesters.aurevanta.problem.ProjectNotFoundException;
import com.cvesters.aurevanta.problem.UnknownElicitationMethodException;
import com.cvesters.aurevanta.problem.WorkItemNotFoundException;
import com.cvesters.aurevanta.project.ProjectService;
import com.cvesters.aurevanta.user.User;

/**
 * Recording what somebody thinks a piece of work will take, and reading back what
 * everyone currently thinks.
 *
 * <p>
 * <strong>There is no update and no delete here, and that is the whole design.</strong>
 * Recording a second estimate for the same item leaves the first exactly where it is; the
 * newer one simply becomes the current one. Calibration asks how often a person's band
 * contained the truth, which is a question about what they said at the time, and only
 * rows nothing rewrites can answer it.
 *
 * <p>
 * Depends on {@code item} and never the other way round: an estimate is <em>of</em> a
 * work item, so this package points at that one, and {@link WorkItemService} is what
 * decides whether the caller may reach the item at all.
 */
@Service
public class EstimateService {

	private final EstimateRepository estimates;

	private final WorkItemService items;

	private final ProjectService projects;

	private final MembershipService memberships;

	private final Clock clock;

	EstimateService(EstimateRepository estimates, WorkItemService items, ProjectService projects,
			MembershipService memberships, Clock clock) {
		this.estimates = estimates;
		this.items = items;
		this.projects = projects;
		this.memberships = memberships;
		this.clock = clock;
	}

	/**
	 * Writes down one person's range for one item.
	 *
	 * <p>
	 * Any member may, including for work somebody else estimated a minute ago: estimation
	 * is a team activity, and two people disagreeing is signal rather than a conflict to
	 * refuse. Their estimates sit side by side, one current per person.
	 * @param method how the three were asked for, which the server cannot observe and so
	 * has to be told — {@link Elicitation} says which names it records
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws WorkItemNotFoundException if no item in it has that identifier
	 * @throws EstimateOutOfOrderException if the three points do not ascend
	 * @throws UnknownElicitationMethodException if the method names nothing this server
	 * records
	 */
	@Transactional
	public Estimate record(UUID callerId, UUID tenantId, UUID itemId, BigDecimal p10Hours, BigDecimal p50Hours,
			BigDecimal p90Hours, String method) {
		requireAscending(p10Hours, p50Hours, p90Hours);
		// A fact about the request alone, like the order above and answered in the same
		// place: a caller who named a method that does not exist learns nothing about
		// which items exist by being told so.
		Elicitation.require(method);
		// The estimator comes off the membership that just proved the caller belongs
		// here,
		// rather than from a second lookup by the identifier in their token: the row is
		// already loaded, and a lookup that could come back empty would be a failure this
		// method cannot actually have — a membership is what says the account exists.
		User estimator = this.memberships.requireMember(callerId, tenantId).getUser();
		WorkItem item = this.items.get(callerId, tenantId, itemId);
		return this.estimates
			.save(new Estimate(item, estimator, p10Hours, p50Hours, p90Hours, method, Instant.now(this.clock)));
	}

	/**
	 * What everybody currently thinks about the work in one plan — one estimate per
	 * person per item, and the revisions they replaced left out.
	 *
	 * <p>
	 * A plan's worth at a time rather than an item's, because a screen showing a plan
	 * needs all of it and asking per item would be five hundred requests to draw one
	 * page.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 * @throws ProjectNotFoundException if no project in it has that identifier
	 */
	@Transactional(readOnly = true)
	public List<Estimate> currentInProject(UUID callerId, UUID tenantId, UUID projectId) {
		this.projects.get(callerId, tenantId, projectId);
		return this.estimates.findCurrentInProject(tenantId, projectId);
	}

	/**
	 * Every range this organisation ever wrote against work that finished and said how
	 * long it took — all of them, oldest first within each pair of item and estimator.
	 *
	 * <p>
	 * <strong>Scoped to one organisation, and that corrects a comment in
	 * {@code V9}</strong>, which says calibration calibrates "across everything they ever
	 * estimated, in whichever organisation". It does not, and it must not:
	 * {@code estimates} carries a {@code tenant_id}, isolation is enforced here, and
	 * reading somebody's estimates from another organisation would be a cross-tenant leak
	 * however true it is that the same person made them. A consultant with two clients
	 * has two records and the two never meet.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public List<ScorableEstimate> scorable(UUID callerId, UUID tenantId) {
		this.memberships.requireMember(callerId, tenantId);
		return this.estimates.findScorableInTenant(tenantId, WorkItemStatus.DONE);
	}

	/**
	 * How many finished items anybody estimated, whether or not anybody measured them.
	 *
	 * <p>
	 * Beside {@code WorkItemService.completedWork}, and the pair of them is what an empty
	 * calibration record is made of: finished work with no estimate can never be scored,
	 * and finished work with no actual cannot be scored yet. Two different things to go
	 * and do about it.
	 * @throws NotAMemberException if the caller no longer belongs to that organisation
	 */
	@Transactional(readOnly = true)
	public long completedItemsEstimated(UUID callerId, UUID tenantId) {
		this.memberships.requireMember(callerId, tenantId);
		return this.estimates.countCompletedItemsEstimated(tenantId, WorkItemStatus.DONE);
	}

	/**
	 * What is worth questioning about a range nobody has committed to yet.
	 *
	 * <p>
	 * <strong>Reads nothing and writes nothing</strong>, which is why it is the one
	 * method here with no membership check: there is no row to reach and no plan to be a
	 * member of. It is arithmetic over three numbers the caller supplied, answered back
	 * to them.
	 *
	 * <p>
	 * It exists because the warning has to arrive <em>before</em> the estimate does. An
	 * estimate is written once and never rewritten, so a form that saved first and warned
	 * afterwards would make "that was not what I meant" cost a second row — and the
	 * moment elicitation is about is the moment somebody is still answering. The
	 * alternative was for the browser to work the flags out itself, which would be two
	 * rules about one estimate that can disagree.
	 * @throws EstimateOutOfOrderException if the three points do not ascend. Shared with
	 * {@link #record}, so the two endpoints refuse the same nonsense — and needed here
	 * rather than optional, since a range that does not ascend has no fit at all.
	 */
	public EstimateQuality quality(BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p90Hours) {
		requireAscending(p10Hours, p50Hours, p90Hours);
		return EstimateQuality.of(p10Hours.doubleValue(), p50Hours.doubleValue(), p90Hours.doubleValue());
	}

	/**
	 * Refuses a band whose ends are the wrong way round.
	 *
	 * <p>
	 * Checked before the item is looked up, deliberately: this is a fact about the
	 * request alone, and a caller who sent nonsense learns nothing about which items
	 * exist by being told so.
	 */
	private static void requireAscending(BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p90Hours) {
		if (p10Hours.compareTo(p50Hours) > 0 || p50Hours.compareTo(p90Hours) > 0) {
			throw new EstimateOutOfOrderException();
		}
	}

}
