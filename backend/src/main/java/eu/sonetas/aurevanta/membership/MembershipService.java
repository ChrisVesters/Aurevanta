package eu.sonetas.aurevanta.membership;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and records which organisations a person belongs to. */
@Service
public class MembershipService {

	private final MembershipRepository memberships;

	private final Clock clock;

	MembershipService(MembershipRepository memberships, Clock clock) {
		this.memberships = memberships;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<Membership> forUser(UUID userId) {
		return this.memberships.findAllForUser(userId);
	}

	/**
	 * Finds the membership a caller is asking to act under and records the choice.
	 *
	 * <p>
	 * The lookup is by user <em>and</em> tenant together, so a tenant identifier supplied
	 * in the request cannot widen the caller's reach — it only picks among organisations
	 * they already belong to. An empty result means exactly that: not a member.
	 */
	@Transactional
	public Optional<Membership> select(UUID userId, UUID tenantId) {
		Optional<Membership> chosen = this.memberships.findForUserInTenant(userId, tenantId);
		chosen.ifPresent((membership) -> membership.recordAccess(Instant.now(this.clock)));
		return chosen;
	}

}
