package com.cvesters.aurevanta.estimate;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cvesters.aurevanta.item.WorkItemStatus;

public interface EstimateRepository extends JpaRepository<Estimate, UUID> {

	/**
	 * The current estimates in one plan: the newest row per item per estimator.
	 *
	 * <p>
	 * Several may be current on one item at the same time, and that is decision 2 working
	 * rather than a fault to resolve here — two people who disagree about a task are
	 * telling M3 something, and it is M3 that decides what to do about it. This query's
	 * job is only to hand back what each of them last said, cheaply: the correlated
	 * {@code max} rides the {@code (work_item_id, estimator_user_id, created_at desc)}
	 * index.
	 *
	 * <p>
	 * Archived items are left out, so coverage and the screen agree about what a plan
	 * contains. The estimator is fetched because their name is what a reader needs and
	 * {@code open-in-view} is off.
	 */
	@Query("""
			select e from Estimate e
			join fetch e.estimator
			join fetch e.workItem w
			where e.tenant.id = :tenantId and w.project.id = :projectId and w.archivedAt is null
			  and e.createdAt = (select max(x.createdAt) from Estimate x
			                     where x.workItem = e.workItem and x.estimator = e.estimator)
			order by w.createdAt asc, e.createdAt asc
			""")
	List<Estimate> findCurrentInProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

	/**
	 * Every range this organisation ever wrote against work that is finished and said how
	 * long it took.
	 *
	 * <p>
	 * <strong>All of them, not the current one per estimator</strong>, which is where
	 * this parts company with the query above. Which bucket an estimate lands in depends
	 * on <em>when</em> it was written — before the work began it is a forecast, after it
	 * is a report by somebody who could already see how the task was going — so the
	 * newest one per pair is exactly the wrong shape, and a flag on that query would have
	 * been one method answering two questions.
	 *
	 * <p>
	 * <strong>Archived items are included</strong>, which is the other line these two
	 * differ on. There it matters that the coverage count and the screen agree about what
	 * a plan holds; here evidence is evidence, and leaving archived work out would make
	 * putting a task away a way to drop a miss.
	 *
	 * <p>
	 * Ordered so that a reader can group in one pass and find the last estimate before
	 * any moment without sorting: by item, by estimator, then oldest first — and then by
	 * identifier, which is what makes it a <em>total</em> order rather than a nearly
	 * total one. Two estimates by one person against one item at the same instant are
	 * otherwise tied, and the tie decides which of them gets scored: that is
	 * {@code ProjectRepository}'s rule about a listing that rearranges itself between
	 * requests, arriving somewhere it changes an answer rather than a sequence.
	 */
	@Query("""
			select new com.cvesters.aurevanta.estimate.ScorableEstimate(
			    e.workItem.id, e.estimator.id, e.estimator.displayName, e.p10Hours, e.p90Hours,
			    e.elicitationMethod, e.createdAt, e.workItem.actualEffortHours)
			from Estimate e
			where e.tenant.id = :tenantId
			  and e.workItem.status = :done and e.workItem.actualEffortHours is not null
			order by e.workItem.id asc, e.estimator.id asc, e.createdAt asc, e.id asc
			""")
	List<ScorableEstimate> findScorableInTenant(@Param("tenantId") UUID tenantId, @Param("done") WorkItemStatus done);

	/**
	 * How many finished items anybody estimated at all, whether or not they said how long
	 * they took.
	 *
	 * <p>
	 * The other half of why a record is empty, beside {@code CompletedWork}: work
	 * finished without an estimate can never be scored, and work finished without an
	 * actual cannot be scored yet. Distinct, because three people estimating one task is
	 * one item covered.
	 */
	@Query("""
			select count(distinct e.workItem.id) from Estimate e
			where e.tenant.id = :tenantId and e.workItem.status = :done
			""")
	long countCompletedItemsEstimated(@Param("tenantId") UUID tenantId, @Param("done") WorkItemStatus done);

}
