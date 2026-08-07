package eu.sonetas.aurevanta.token;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import eu.sonetas.aurevanta.user.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

	/**
	 * Marks a token spent, and reports whether this caller is the one that spent it.
	 *
	 * <p>
	 * Written as a single conditional update rather than a read followed by a write,
	 * because that is what makes "exactly once" hold when two requests arrive together:
	 * the database locks the row and re-checks {@code consumed_at is null}, so the second
	 * one updates nothing and gets 0 back. Reading first would let both see an unspent
	 * token and both proceed.
	 *
	 * <p>
	 * Expiry is part of the same predicate for the same reason — checking it separately
	 * would leave a window in which a token expires between the check and the update.
	 * @return 1 if this call spent the token, 0 if it was unknown, expired, already
	 * spent, or issued for another purpose
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update UserToken t set t.consumedAt = :now
			where t.tokenHash = :tokenHash
			  and t.purpose = :purpose
			  and t.consumedAt is null
			  and t.expiresAt > :now
			""")
	int consume(@Param("tokenHash") String tokenHash, @Param("purpose") TokenPurpose purpose,
			@Param("now") Instant now);

	/**
	 * The person a token belongs to. Only called once {@link #consume} has already
	 * established that this caller spent it, so it never decides anything by itself.
	 */
	@Query("select t.user from UserToken t where t.tokenHash = :tokenHash")
	Optional<User> findUserByTokenHash(@Param("tokenHash") String tokenHash);

}
