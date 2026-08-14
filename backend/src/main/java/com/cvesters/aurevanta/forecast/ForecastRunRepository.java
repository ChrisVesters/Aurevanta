package com.cvesters.aurevanta.forecast;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ForecastRunRepository extends JpaRepository<ForecastRun, UUID> {

	/**
	 * Every forecast of one plan, newest first — which is the order both readers want.
	 * The history screen shows the latest and what it moved from, and M10's sliding-date
	 * detector walks back through them looking for a date that keeps going out.
	 *
	 * <p>
	 * The requester is fetched because their name is what a reader needs, and
	 * {@code open-in-view} is off. Not paginated: a plan accumulates forecasts at the
	 * rate somebody presses a button.
	 */
	@Query("""
			select r from ForecastRun r
			join fetch r.requestedBy
			where r.tenant.id = :tenantId and r.project.id = :projectId
			order by r.createdAt desc, r.id desc
			""")
	List<ForecastRun> findAllInProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);

	/**
	 * One run a caller named by identifier, looked up by identifier <em>and</em> tenant
	 * together — so an identifier from another organisation selects nothing rather than
	 * somebody else's forecast.
	 */
	@Query("""
			select r from ForecastRun r
			join fetch r.requestedBy
			join fetch r.project
			where r.id = :id and r.tenant.id = :tenantId
			""")
	Optional<ForecastRun> findInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

}
