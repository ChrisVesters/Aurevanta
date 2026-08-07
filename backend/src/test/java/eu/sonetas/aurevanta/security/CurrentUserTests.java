package eu.sonetas.aurevanta.security;

import java.util.UUID;

import eu.sonetas.aurevanta.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CurrentUser} is where tenant isolation is enforced, so every path that could
 * hand out a tenant — or wrongly refuse to — is pinned down here.
 */
class CurrentUserTests {

	private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final UUID TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

	private final CurrentUser currentUser = new CurrentUser();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void findsNobodyWhenTheContextIsEmpty() {
		assertThat(this.currentUser.find()).isEmpty();
	}

	@Test
	void findsNobodyWhenTheAuthenticationIsNotYetAuthenticated() {
		// The two-argument constructor deliberately produces an unauthenticated token.
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("ada@acme.test", "secret"));

		assertThat(this.currentUser.find()).isEmpty();
	}

	@Test
	void findsNobodyWhenThePrincipalCameFromAnotherMechanism() {
		Authentication foreign = UsernamePasswordAuthenticationToken.authenticated("ada@acme.test", null,
				java.util.List.of());
		SecurityContextHolder.getContext().setAuthentication(foreign);

		assertThat(this.currentUser.find()).isEmpty();
	}

	@Test
	void findsTheAuthenticatedUser() {
		authenticate(UserRole.OWNER);

		assertThat(this.currentUser.find())
			.contains(new AuthenticatedUser(USER_ID, TENANT_ID, "ada@acme.test", UserRole.OWNER));
	}

	@Test
	void requireReturnsTheAuthenticatedUser() {
		authenticate(UserRole.MEMBER);

		assertThat(this.currentUser.require().userId()).isEqualTo(USER_ID);
	}

	@Test
	void requireRefusesWhenNobodyIsAuthenticated() {
		assertThatThrownBy(this.currentUser::require).isInstanceOf(AccessDeniedException.class)
			.hasMessageContaining("No authenticated user");
	}

	@Test
	void requiredTenantIdIsTheTenantPinnedIntoTheToken() {
		authenticate(UserRole.OWNER);

		assertThat(this.currentUser.requiredTenantId()).isEqualTo(TENANT_ID);
	}

	@Test
	void requiredTenantIdRefusesWhenNobodyIsAuthenticated() {
		assertThatThrownBy(this.currentUser::requiredTenantId).isInstanceOf(AccessDeniedException.class);
	}

	/**
	 * An identity token authenticates a person but names no organisation. Handing back a
	 * null tenant would let a query run unscoped, so the refusal has to happen here.
	 */
	@Test
	void requiredTenantIdRefusesACallerWhoHasNotChosenAnOrganisation() {
		authenticate(new AuthenticatedUser(USER_ID, null, "ada@acme.test", null));

		assertThatThrownBy(this.currentUser::requiredTenantId).isInstanceOf(AccessDeniedException.class)
			.hasMessageContaining("not scoped to an organisation");
	}

	@Test
	void requireStillReturnsACallerWhoHasNotChosenAnOrganisation() {
		authenticate(new AuthenticatedUser(USER_ID, null, "ada@acme.test", null));

		assertThat(this.currentUser.require().hasTenant()).isFalse();
	}

	private void authenticate(UserRole role) {
		authenticate(new AuthenticatedUser(USER_ID, TENANT_ID, "ada@acme.test", role));
	}

	private void authenticate(AuthenticatedUser principal) {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject(USER_ID.toString()).build();
		SecurityContextHolder.getContext()
			.setAuthentication(new AurevantaAuthenticationToken(principal, jwt, java.util.List.of()));
	}

}
