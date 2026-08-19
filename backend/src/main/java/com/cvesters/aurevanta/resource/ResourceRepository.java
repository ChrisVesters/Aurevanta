package com.cvesters.aurevanta.resource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

	/**
	 * The pools of one organisation, in one state or the other.
	 *
	 * <p>
	 * One query for both halves of the listing, as {@code ProjectRepository} keeps one:
	 * they differ only in which side of {@code archived_at is null} they want, and two
	 * methods would be two places to forget the tenant.
	 *
	 * <p>
	 * <strong>Ordered by when it was declared and not by name</strong>, which is the one
	 * place this listing differs from the plans one. Declaration order is part of a
	 * modelling rule — an item that names no pool takes one unit of the first that has
	 * one free — so an order settled by name would mean renaming a pool changed what a
	 * forecast scheduled. The identifier makes it total, as it does everywhere else.
	 */
	@Query("""
			select r from Resource r
			join fetch r.tenant
			left join fetch r.person
			where r.tenant.id = :tenantId
			  and ((:archived = true and r.archivedAt is not null)
			    or (:archived = false and r.archivedAt is null))
			order by r.createdAt asc, r.id asc
			""")
	List<Resource> findAllInTenant(@Param("tenantId") UUID tenantId, @Param("archived") boolean archived);

	/**
	 * One pool, looked up by identifier <em>and</em> tenant together — the pairing that
	 * is the isolation rule, so an identifier from another organisation selects nothing
	 * rather than somebody else's team.
	 */
	@Query("""
			select r from Resource r
			join fetch r.tenant
			left join fetch r.person
			where r.id = :id and r.tenant.id = :tenantId
			""")
	Optional<Resource> findInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

}
