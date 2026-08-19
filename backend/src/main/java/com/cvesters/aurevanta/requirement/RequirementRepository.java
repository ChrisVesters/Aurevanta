package com.cvesters.aurevanta.requirement;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

	/**
	 * What one piece of work needs, in the order its pools were declared.
	 *
	 * <p>
	 * The order matters for the same reason the pools' own listing is in declaration
	 * order rather than by name: it is what a reader compares against a screen, and a
	 * listing that rearranged itself when somebody renamed a pool would read as the plan
	 * having changed.
	 */
	@Query("""
			select r from Requirement r
			join fetch r.resource
			where r.workItem.id = :workItemId and r.tenant.id = :tenantId
			order by r.resource.createdAt asc, r.resource.id asc
			""")
	List<Requirement> findAllForItem(@Param("workItemId") UUID workItemId, @Param("tenantId") UUID tenantId);

	/**
	 * Everything a whole plan needs, which is how a screen reads it.
	 *
	 * <p>
	 * <strong>A plan's worth at a time, for {@code estimate}'s reason</strong>: asking
	 * per item would be five hundred requests to draw one page. The join is through the
	 * item, so no {@code project_id} is carried here — a column that has to be kept
	 * agreeing with the item's own is an invariant this table does not need.
	 *
	 * <p>
	 * Archived items are included, exactly as their estimates are: what a screen does
	 * with work somebody has put away is the screen's decision, and a listing that had
	 * already made it would leave the archived view unable to say what its work needed.
	 */
	@Query("""
			select r from Requirement r
			join fetch r.resource
			join fetch r.workItem
			where r.workItem.project.id = :projectId and r.tenant.id = :tenantId
			order by r.workItem.createdAt asc, r.resource.createdAt asc, r.resource.id asc
			""")
	List<Requirement> findAllInProject(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);

	/**
	 * Clears what an item needs, so that the whole set can be written in its place.
	 *
	 * <p>
	 * A requirement carries no history anything downstream reads — a forecast copies the
	 * declaration onto the run rather than reading this table later — so replacing a set
	 * is a delete and an insert rather than a diff. That is {@code dependency}'s argument
	 * for being the one thing in this domain that really is deleted, arriving in a second
	 * place: this is a constraint the scheduler obeys until it is changed, not a record
	 * of what anybody once believed.
	 */
	@Modifying
	@Query("""
			delete from Requirement r
			where r.workItem.id = :workItemId and r.tenant.id = :tenantId
			""")
	void deleteAllForItem(@Param("workItemId") UUID workItemId, @Param("tenantId") UUID tenantId);

}
