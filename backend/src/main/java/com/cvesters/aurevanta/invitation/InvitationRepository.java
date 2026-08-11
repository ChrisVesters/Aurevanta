package com.cvesters.aurevanta.invitation;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
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

}
