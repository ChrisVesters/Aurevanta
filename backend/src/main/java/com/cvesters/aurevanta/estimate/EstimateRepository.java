package com.cvesters.aurevanta.estimate;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

}
