package com.cvesters.aurevanta.dependency;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DependencyRepository extends JpaRepository<Dependency, UUID> {

	/**
	 * Every edge in one plan.
	 *
	 * <p>
	 * Read whole rather than walked a neighbour at a time, because the walk that follows
	 * it asks "can the successor already reach the predecessor" and a query per hop would
	 * be a query per hop under a lock. A plan is 500 items by decision, so the whole
	 * graph is small enough to be a list.
	 *
	 * <p>
	 * Both ends are fetched: the caller reads their identifiers, and {@code open-in-view}
	 * is off.
	 */
	@Query("""
			select d from Dependency d
			join fetch d.predecessor
			join fetch d.successor
			where d.tenant.id = :tenantId and d.project.id = :projectId
			order by d.createdAt asc, d.id asc
			""")
	List<Dependency> findAllInProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

	/**
	 * One edge a caller named by identifier, looked up by identifier <em>and</em> tenant
	 * together — so an identifier from another organisation selects nothing.
	 */
	@Query("""
			select d from Dependency d
			join fetch d.predecessor
			join fetch d.successor
			where d.id = :id and d.tenant.id = :tenantId
			""")
	Optional<Dependency> findInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	/** Whether this exact arrow has already been drawn, which is what the index holds. */
	@Query("""
			select count(d) > 0 from Dependency d
			where d.predecessor.id = :predecessorId and d.successor.id = :successorId
			""")
	boolean existsBetween(@Param("predecessorId") UUID predecessorId, @Param("successorId") UUID successorId);

}
