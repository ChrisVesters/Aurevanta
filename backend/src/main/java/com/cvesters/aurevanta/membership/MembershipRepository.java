package com.cvesters.aurevanta.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cvesters.aurevanta.user.UserRole;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

	/**
	 * Every organisation the person belongs to, most recently used first, so a client can
	 * offer the obvious default without deciding an order of its own. User and tenant are
	 * fetched because {@code open-in-view} is off and both are read after the transaction
	 * closes.
	 */
	@Query("""
			select m from Membership m
			join fetch m.user
			join fetch m.tenant t
			where m.user.id = :userId
			order by m.lastAccessedAt desc nulls last, t.name asc
			""")
	List<Membership> findAllForUser(@Param("userId") UUID userId);

	/**
	 * The caller's standing in one organisation, looked up by user <em>and</em> tenant
	 * together. That pairing is what makes a tenant id taken from a request safe: it can
	 * only ever select among organisations the caller already belongs to.
	 */
	@Query("""
			select m from Membership m
			join fetch m.user
			join fetch m.tenant
			where m.user.id = :userId and m.tenant.id = :tenantId
			""")
	Optional<Membership> findForUserInTenant(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

	/**
	 * Whether an address already belongs to one organisation.
	 *
	 * <p>
	 * Scoped by tenant, and that scoping is the point rather than an optimisation: an
	 * owner inviting somebody may be told that address is already in <em>their</em>
	 * organisation, because they can list its members anyway. Whether it holds an account
	 * elsewhere is not something an invitation may be used to ask.
	 *
	 * <p>
	 * Matched on {@code lower(email)} to agree with {@code uq_users_email}, so an
	 * invitation cannot get past this by differing in case from the member it duplicates.
	 */
	@Query("""
			select count(m) > 0 from Membership m
			where m.tenant.id = :tenantId and lower(m.user.email) = lower(:email)
			""")
	boolean existsInTenantByEmailIgnoringCase(@Param("tenantId") UUID tenantId, @Param("email") String email);

	/**
	 * How many people hold a role in an organisation. Counting owners is what stops the
	 * last one being demoted or removed, leaving nobody able to administer it.
	 */
	long countByTenantIdAndRole(UUID tenantId, UserRole role);

}
