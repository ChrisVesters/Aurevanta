package eu.sonetas.aurevanta.security;

import java.util.UUID;

import eu.sonetas.aurevanta.user.UserRole;
import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A verified signature only proves the token is ours; the claims inside still have to be
 * usable. These are the checks that stand between a malformed claim and a tenant
 * assignment, so each rejection path is asserted rather than assumed.
 */
class AuthenticatedUserJwtConverterTests {

	private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

	private static final String TENANT_ID = "22222222-2222-2222-2222-222222222222";

	private final AuthenticatedUserJwtConverter converter = new AuthenticatedUserJwtConverter();

	@Test
	void convertsAWellFormedToken() {
		AbstractAuthenticationToken authentication = this.converter.convert(token().build());

		assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(UUID.fromString(USER_ID),
				UUID.fromString(TENANT_ID), "ada@acme.test", UserRole.OWNER));
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getName()).isEqualTo("ada@acme.test");
	}

	@Test
	void grantsAnAuthorityNamedAfterTheRole() {
		AbstractAuthenticationToken authentication = this.converter
			.convert(token().claim(TokenClaims.ROLE, "MEMBER").build());

		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MEMBER");
	}

	@Test
	void keepsTheVerifiedTokenAsCredentials() {
		Jwt jwt = token().build();

		assertThat(this.converter.convert(jwt).getCredentials()).isSameAs(jwt);
	}

	@Test
	void rejectsATokenWithoutASubject() {
		assertThatRejects(Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.claim(TokenClaims.ROLE, "OWNER")
			.build(), "sub");
	}

	@Test
	void rejectsASubjectThatIsNotAnIdentifier() {
		assertThatRejects(token().subject("not-a-uuid").build(), "sub");
	}

	@Test
	void rejectsATokenWithoutATenant() {
		assertThatRejects(token().claim(TokenClaims.TENANT_ID, "").build(), TokenClaims.TENANT_ID);
	}

	@Test
	void rejectsATenantThatIsNotAnIdentifier() {
		assertThatRejects(token().claim(TokenClaims.TENANT_ID, "not-a-uuid").build(), TokenClaims.TENANT_ID);
	}

	@Test
	void rejectsATokenWithoutAnEmail() {
		assertThatRejects(token().claim(TokenClaims.EMAIL, " ").build(), TokenClaims.EMAIL);
	}

	@Test
	void rejectsATokenWithoutARole() {
		assertThatRejects(Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(USER_ID)
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.build(), TokenClaims.ROLE);
	}

	@Test
	void rejectsARoleThisApplicationDoesNotIssue() {
		assertThatRejects(token().claim(TokenClaims.ROLE, "SUPERUSER").build(), TokenClaims.ROLE);
	}

	private void assertThatRejects(Jwt jwt, String claim) {
		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class)
			.hasMessageContaining(claim);
	}

	private Jwt.Builder token() {
		return Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(USER_ID)
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.claim(TokenClaims.ROLE, "OWNER");
	}

}
