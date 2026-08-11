package com.cvesters.aurevanta.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
	 * Everyone in one organisation, by display name so the list does not reorder itself
	 * between requests — and by address after it, because two people may well share a
	 * name and an order that is only <em>mostly</em> decided is one that flickers. User
	 * and tenant are fetched because both are read after the transaction closes.
	 */
	@Query("""
			select m from Membership m
			join fetch m.user u
			join fetch m.tenant
			where m.tenant.id = :tenantId
			order by u.displayName asc, u.email asc
			""")
	List<Membership> findAllInTenant(@Param("tenantId") UUID tenantId);

	/**
	 * One membership an owner named by identifier, looked up by identifier <em>and</em>
	 * tenant together. That pairing is what keeps an identifier taken from a request from
	 * selecting anybody outside the caller's own organisation.
	 */
	@Query("""
			select m from Membership m
			join fetch m.user
			join fetch m.tenant
			where m.id = :id and m.tenant.id = :tenantId
			""")
	Optional<Membership> findInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	/**
	 * The owners of one organisation, locked for update.
	 *
	 * <p>
	 * Counting them is what stops the last one being demoted or removed — and counting
	 * without a lock would not, because the hazard is two owners acting at once. Each
	 * would count two, each would conclude one remains, and the organisation would be
	 * left with none. That is the one failure here nothing inside the product can repair,
	 * so it is worth a lock rather than an optimistic check.
	 *
	 * <p>
	 * A conditional {@code UPDATE} cannot serve instead, the way it can for spending a
	 * token: that makes a race on <em>one row</em> safe, and this is a race on a count
	 * across several. The second transaction has to wait and then count again, which is
	 * what {@code for update} makes it do.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select m from Membership m
			where m.tenant.id = :tenantId
			  and m.role = com.cvesters.aurevanta.user.UserRole.OWNER
			""")
	List<Membership> lockOwners(@Param("tenantId") UUID tenantId);

}
