package com.cvesters.aurevanta.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

	/**
	 * The projects of one organisation, in one state or the other.
	 *
	 * <p>
	 * Both halves of the listing are this one query because they differ only in which
	 * side of {@code archived_at is null} they want, and two methods would be two places
	 * to forget the tenant. No pagination: a plan is 500 items and an organisation's
	 * projects are fewer, which {@code m2-plan.md} fixes precisely so that this question
	 * has an answer.
	 *
	 * <p>
	 * Ordered by name, then by when it was made, then by identifier — a name is not
	 * unique here, so an order settled only by name is one that rearranges itself between
	 * requests. The identifier is what makes it total rather than nearly so.
	 */
	@Query("""
			select p from Project p
			join fetch p.tenant
			where p.tenant.id = :tenantId
			  and ((:archived = true and p.archivedAt is not null)
			    or (:archived = false and p.archivedAt is null))
			order by p.name asc, p.createdAt asc, p.id asc
			""")
	List<Project> findAllInTenant(@Param("tenantId") UUID tenantId, @Param("archived") boolean archived);

	/**
	 * One project a caller named by identifier, looked up by identifier <em>and</em>
	 * tenant together. That pairing is the isolation rule: an identifier taken from a
	 * request can only ever select something the caller's own organisation owns, so one
	 * from another selects nothing rather than somebody else's plan.
	 */
	@Query("""
			select p from Project p
			join fetch p.tenant
			where p.id = :id and p.tenant.id = :tenantId
			""")
	Optional<Project> findInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	/**
	 * How many items each of the organisation's plans holds, archived ones left out.
	 *
	 * <p>
	 * One grouped query for the whole organisation rather than a count per project, so a
	 * list of plans costs the same as one.
	 */
	@Query("""
			select new com.cvesters.aurevanta.project.ProjectCount(w.project.id, count(w))
			from WorkItem w
			where w.tenant.id = :tenantId and w.archivedAt is null
			group by w.project.id
			""")
	List<ProjectCount> itemCounts(@Param("tenantId") UUID tenantId);

	/**
	 * How many of those items anybody has estimated. Distinct, because several people may
	 * hold a current estimate on one item and that is one item covered, not three.
	 *
	 * <p>
	 * This query and the one above are the only place in the application where one
	 * feature reaches into another's tables. They name {@code WorkItem} and
	 * {@code Estimate} in JPQL and import neither, which is a coupling worth being
	 * explicit about — and one that cannot go quietly stale, because Hibernate parses
	 * every query at startup, so a renamed entity fails the context rather than the next
	 * reader.
	 */
	@Query("""
			select new com.cvesters.aurevanta.project.ProjectCount(
			    e.workItem.project.id, count(distinct e.workItem.id))
			from Estimate e
			where e.tenant.id = :tenantId and e.workItem.archivedAt is null
			group by e.workItem.project.id
			""")
	List<ProjectCount> estimatedItemCounts(@Param("tenantId") UUID tenantId);

}
