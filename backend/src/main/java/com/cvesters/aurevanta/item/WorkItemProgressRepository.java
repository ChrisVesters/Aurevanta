package com.cvesters.aurevanta.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The history of what has been claimed about a piece of work.
 *
 * <p>
 * Its own repository rather than more methods on {@code WorkItemRepository}: the log is a
 * second table with a second lifetime — append-only where the item is written over — and
 * an item's own queries have no business in it.
 */
public interface WorkItemProgressRepository extends JpaRepository<WorkItemProgress, UUID> {

	/**
	 * Every claim made about one item, newest first.
	 *
	 * <p>
	 * Scoped by tenant as well as item, so an identifier from another organisation
	 * selects nothing rather than somebody else's history. The reporter is fetched
	 * because their name is the whole point of reading this and {@code open-in-view} is
	 * off.
	 *
	 * <p>
	 * Not limited to the newest, although the only thing on screen is one line. The
	 * resource is the log; a truncating query would need a rule about how much history is
	 * worth keeping, which is a question nobody asked and which this table exists to stop
	 * anybody answering by accident. It is bounded by how often one item is reported on.
	 */
	@Query("""
			select p from WorkItemProgress p
			join fetch p.reportedBy
			where p.tenant.id = :tenantId and p.workItem.id = :itemId
			order by p.reportedAt desc, p.id desc
			""")
	List<WorkItemProgress> findForItem(@Param("tenantId") UUID tenantId, @Param("itemId") UUID itemId);

	/**
	 * The earliest start ever claimed for each item in one organisation, for the items
	 * anybody has claimed one for.
	 *
	 * <p>
	 * One grouped query for a whole organisation rather than a lookup per item, the way
	 * {@code ProjectRepository.itemCounts} is: M8 scores every completed item at once, so
	 * asking per item would be a query per piece of work a team has ever finished.
	 *
	 * <p>
	 * Reports that claim no start are left out rather than counted as a start of nothing
	 * — {@code min} would ignore them anyway, and saying so in the query is what stops
	 * somebody reading the absence of a row as a claim.
	 */
	@Query("""
			select new com.cvesters.aurevanta.item.ReportedStart(p.workItem.id, min(p.startedOn))
			from WorkItemProgress p
			where p.tenant.id = :tenantId and p.startedOn is not null
			group by p.workItem.id
			""")
	List<ReportedStart> earliestReportedStarts(@Param("tenantId") UUID tenantId);

}
