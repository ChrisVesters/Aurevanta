package com.cvesters.aurevanta.item;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> {

	/**
	 * The items of one plan, in one state or the other.
	 *
	 * <p>
	 * Constrained by tenant <em>as well as</em> project, which is what the denormalised
	 * {@code tenant_id} is for: the scoping does not depend on the project having been
	 * looked up correctly somewhere else.
	 *
	 * <p>
	 * Ordered by when each was written down, which is the order somebody typed their plan
	 * in and the only order this row carries — a title is not something anybody wants
	 * sorted alphabetically, and step 5's dependencies are the real structure. Not
	 * paginated: 500 items to a project is the fixed ceiling, and it exists so that one
	 * response is always enough.
	 */
	@Query("""
			select w from WorkItem w
			join fetch w.project
			where w.tenant.id = :tenantId and w.project.id = :projectId
			  and ((:archived = true and w.archivedAt is not null)
			    or (:archived = false and w.archivedAt is null))
			order by w.createdAt asc, w.id asc
			""")
	List<WorkItem> findAllInProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId,
			@Param("archived") boolean archived);

	/**
	 * One item a caller named by identifier, looked up by identifier <em>and</em> tenant
	 * together — so an identifier from another organisation selects nothing rather than
	 * somebody else's work.
	 */
	@Query("""
			select w from WorkItem w
			join fetch w.project
			where w.id = :id and w.tenant.id = :tenantId
			""")
	Optional<WorkItem> findInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	/**
	 * The start date each item currently holds, for the items holding one.
	 *
	 * <p>
	 * <strong>The fallback half of the boundary M8 measures against</strong>, and it
	 * exists because {@code V16} deliberately backfilled nothing: an item whose progress
	 * was recorded before that migration has a start date and no report to go with it,
	 * and this column is then the only claim anybody ever made about it. Where both
	 * exist, {@code WorkItemService.earliestReportedStarts} takes the earlier — so this
	 * is one of two claims to be weighed rather than an alternative to the log.
	 */
	@Query("""
			select new com.cvesters.aurevanta.item.ReportedStart(w.id, w.startedOn)
			from WorkItem w
			where w.tenant.id = :tenantId and w.startedOn is not null
			""")
	List<ReportedStart> currentStarts(@Param("tenantId") UUID tenantId);

	/**
	 * How much of this organisation's finished work says how long it took.
	 *
	 * <p>
	 * Archived items are counted, unlike everywhere a listing is drawn. Evidence is
	 * evidence: excluding it would make putting work away a means of leaving a record,
	 * and the whole value of a hit rate is that nothing about it can be arranged.
	 *
	 * <p>
	 * {@code count} over a nullable column counts the rows that filled it in, so both
	 * figures come from one pass rather than from two queries that could disagree about
	 * which items are finished.
	 */
	@Query("""
			select new com.cvesters.aurevanta.item.CompletedWork(count(w), count(w.actualEffortHours))
			from WorkItem w
			where w.tenant.id = :tenantId and w.status = :done
			""")
	CompletedWork completedWork(@Param("tenantId") UUID tenantId, @Param("done") WorkItemStatus done);

	/**
	 * The day each of one plan's finished items was reported finished, oldest first.
	 *
	 * <p>
	 * <strong>Archived items are here, and the query below them deliberately leaves
	 * archived items out.</strong> The two rules point in opposite directions and both
	 * are right: a task that was delivered and later put away was still delivered, so
	 * dropping it would make tidying up look like a slowdown — while a task put away
	 * before it was finished is not going to be delivered at all, so it is not in the
	 * backlog. A single "ignore archived" would be wrong in one of the two places and
	 * would look right in both.
	 *
	 * <p>
	 * A completion date is required on anything marked done, so the null check is not a
	 * filter on ordinary data — it is what stops a row written outside the service
	 * putting a null into a list of days. Such an item is not finished work being
	 * ignored; it is finished work nobody can place in a week.
	 */
	@Query("""
			select w.completedOn from WorkItem w
			where w.tenant.id = :tenantId and w.project.id = :projectId
			  and w.status = :done and w.completedOn is not null
			order by w.completedOn asc
			""")
	List<LocalDate> completionsInProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId,
			@Param("done") WorkItemStatus done);

	/**
	 * How much of one plan is still to be delivered.
	 *
	 * <p>
	 * Everything not finished and not put away, whether or not anybody estimated it —
	 * <strong>which is the one place a throughput forecast is better informed than the
	 * engine.</strong> An unestimated item is a hole in a band and reports itself as a
	 * limitation; here it is an item like any other, because what is being counted is
	 * work left rather than effort left.
	 */
	@Query("""
			select count(w) from WorkItem w
			where w.tenant.id = :tenantId and w.project.id = :projectId
			  and w.status <> :done and w.archivedAt is null
			""")
	long countRemainingInProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId,
			@Param("done") WorkItemStatus done);

}
