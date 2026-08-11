package com.cvesters.aurevanta.invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

	/**
	 * The outstanding invitation this address holds to this organisation, if any.
	 *
	 * <p>
	 * Matched on {@code lower(email)} to agree with {@code uq_invitations_pending}, and
	 * scoped by tenant as well: an invitation to one organisation is not something
	 * another organisation may be told about. The tenant comes from the caller's own
	 * token, never from the request.
	 *
	 * <p>
	 * Returns the row whether or not it has expired, because expiry decides what happens
	 * next rather than whether there is anything there — the unique index counts an
	 * expired invitation as occupying the slot, so the caller has to see it.
	 */
	@Query("""
			select i from Invitation i
			join fetch i.tenant
			join fetch i.invitedBy
			where i.tenant.id = :tenantId
			  and lower(i.email) = lower(:email)
			  and i.status = com.cvesters.aurevanta.invitation.InvitationStatus.PENDING
			""")
	Optional<Invitation> findPending(@Param("tenantId") UUID tenantId, @Param("email") String email);

	/**
	 * The invitation an emailed link names, in whatever state it is in.
	 *
	 * <p>
	 * Deliberately not filtered by status or expiry: what is wrong with a link decides
	 * what the visitor is told, and a query that returned nothing for a withdrawn
	 * invitation could only ever answer "that link does not work".
	 *
	 * <p>
	 * Organisation and inviter are fetched because the preview names both and
	 * {@code open-in-view} is off.
	 */
	@Query("""
			select i from Invitation i
			join fetch i.tenant
			join fetch i.invitedBy
			where i.tokenHash = :tokenHash
			""")
	Optional<Invitation> findByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * Everything still outstanding in one organisation, most recently sent first, and by
	 * address after that: two invitations sent in the same moment would otherwise come
	 * back in whatever order the database felt like, which is an order that changes.
	 */
	@Query("""
			select i from Invitation i
			join fetch i.tenant
			join fetch i.invitedBy
			where i.tenant.id = :tenantId
			  and i.status = com.cvesters.aurevanta.invitation.InvitationStatus.PENDING
			order by i.createdAt desc, i.email asc
			""")
	List<Invitation> findPendingForTenant(@Param("tenantId") UUID tenantId);

	/**
	 * One outstanding invitation an owner named by identifier.
	 *
	 * <p>
	 * Looked up by identifier <em>and</em> tenant together, for the same reason the
	 * membership lookup is: an identifier taken from a request must only ever select
	 * among rows the caller can already see, or revoking becomes a way to reach into
	 * another organisation.
	 */
	@Query("""
			select i from Invitation i
			join fetch i.tenant
			join fetch i.invitedBy
			where i.id = :id and i.tenant.id = :tenantId
			  and i.status = com.cvesters.aurevanta.invitation.InvitationStatus.PENDING
			""")
	Optional<Invitation> findPendingInTenant(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

	/**
	 * Spends an invitation, and reports whether this caller is the one that spent it.
	 *
	 * <p>
	 * A single conditional update rather than a read followed by a write, exactly as
	 * {@code user_tokens} redeems its links: two people clicking the same invitation at
	 * once would otherwise both see a pending row and both be given a membership, and the
	 * second would be a duplicate the unique index on {@code memberships} would then
	 * refuse — after the first had already been told it worked.
	 * @return 1 if this call spent it, 0 if it was already accepted, withdrawn or expired
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update Invitation i
			set i.status = com.cvesters.aurevanta.invitation.InvitationStatus.ACCEPTED, i.acceptedAt = :now
			where i.id = :id
			  and i.status = com.cvesters.aurevanta.invitation.InvitationStatus.PENDING
			  and i.expiresAt > :now
			""")
	int markAccepted(@Param("id") UUID id, @Param("now") Instant now);

}
