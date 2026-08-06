package eu.sonetas.aurevanta.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

	/**
	 * Case-insensitive lookup written as {@code lower(email)} to match the unique index,
	 * so login is an index probe rather than a scan of every account.
	 */
	@Query("select u from User u join fetch u.tenant where lower(u.email) = lower(:email)")
	Optional<User> findWithTenantByEmailIgnoringCase(@Param("email") String email);

	@Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
	boolean existsByEmailIgnoringCase(@Param("email") String email);

	/**
	 * Loads a user together with their tenant; needed because {@code open-in-view} is off
	 * and the association is lazy.
	 */
	@EntityGraph(attributePaths = "tenant")
	Optional<User> findWithTenantById(UUID id);

}
