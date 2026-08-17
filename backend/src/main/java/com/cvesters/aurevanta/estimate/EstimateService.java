package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.item.WorkItemService;
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
 * newer one simply becomes the current one. M8 asks how often a person's band contained
 * the truth, which is a question about what they said at the time, and only rows nothing
 * rewrites can answer it.
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
