package com.cvesters.aurevanta.security;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.cvesters.aurevanta.security.AuthenticatedUser;
import com.cvesters.aurevanta.security.AuthenticatedUserJwtConverter;
import com.cvesters.aurevanta.security.Authorities;
import com.cvesters.aurevanta.security.TokenClaims;
import com.cvesters.aurevanta.user.UserRole;

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
	void convertsAWellFormedAccessToken() {
		AbstractAuthenticationToken authentication = this.converter.convert(accessToken().build());

		assertThat(authentication.getPrincipal()).isEqualTo(new AuthenticatedUser(UUID.fromString(USER_ID),
				UUID.fromString(TENANT_ID), "ada@acme.test", UserRole.OWNER));
		assertThat(authentication.isAuthenticated()).isTrue();
		assertThat(authentication.getName()).isEqualTo("ada@acme.test");
	}

	@Test
	void grantsAnAccessTokenTheTenantScopeAndAnAuthorityNamedAfterTheRole() {
		AbstractAuthenticationToken authentication = this.converter
			.convert(accessToken().claim(TokenClaims.ROLE, "MEMBER").build());

		assertThat(authentication.getAuthorities()).extracting(Object::toString)
			.containsExactlyInAnyOrder(Authorities.TENANT_SCOPED, "ROLE_MEMBER");
	}

	@Test
	void keepsTheVerifiedTokenAsCredentials() {
		Jwt jwt = accessToken().build();

		assertThat(this.converter.convert(jwt).getCredentials()).isSameAs(jwt);
	}

	/**
	 * The whole point of an identity token: it names a person and no organisation, so
	 * nothing downstream can read a tenant out of it by mistake.
	 */
	@Test
	void convertsAnIdentityTokenIntoAPrincipalWithNoTenantAndNoRole() {
		AbstractAuthenticationToken authentication = this.converter.convert(identityToken().build());

		AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
		assertThat(principal.userId()).isEqualTo(UUID.fromString(USER_ID));
		assertThat(principal.email()).isEqualTo("ada@acme.test");
		assertThat(principal.tenantId()).isNull();
		assertThat(principal.role()).isNull();
		assertThat(principal.hasTenant()).isFalse();
	}

	@Test
	void grantsAnIdentityTokenNothingButTheIdentityScope() {
		AbstractAuthenticationToken authentication = this.converter.convert(identityToken().build());

		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly(Authorities.IDENTITY);
	}

	/** A tenant claim on an identity token must not promote it; the type decides. */
	@Test
	void ignoresATenantClaimSmuggledOntoAnIdentityToken() {
		AbstractAuthenticationToken authentication = this.converter
			.convert(identityToken().claim(TokenClaims.TENANT_ID, TENANT_ID).claim(TokenClaims.ROLE, "OWNER").build());

		assertThat(((AuthenticatedUser) authentication.getPrincipal()).hasTenant()).isFalse();
		assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly(Authorities.IDENTITY);
	}

	@Test
	void rejectsATokenWithoutASubject() {
		assertThatRejects(Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.claim(TokenClaims.TOKEN_TYPE, TokenClaims.ACCESS)
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.claim(TokenClaims.ROLE, "OWNER")
			.build(), "sub");
	}

	@Test
	void rejectsASubjectThatIsNotAnIdentifier() {
		assertThatRejects(accessToken().subject("not-a-uuid").build(), "sub");
	}

	@Test
	void rejectsAnAccessTokenWithoutATenant() {
		assertThatRejects(accessToken().claim(TokenClaims.TENANT_ID, "").build(), TokenClaims.TENANT_ID);
	}

	@Test
	void rejectsATenantThatIsNotAnIdentifier() {
		assertThatRejects(accessToken().claim(TokenClaims.TENANT_ID, "not-a-uuid").build(), TokenClaims.TENANT_ID);
	}

	@Test
	void rejectsATokenWithoutAnEmail() {
		assertThatRejects(accessToken().claim(TokenClaims.EMAIL, " ").build(), TokenClaims.EMAIL);
	}

	@Test
	void rejectsAnAccessTokenWithoutARole() {
		assertThatRejects(Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(USER_ID)
			.claim(TokenClaims.TOKEN_TYPE, TokenClaims.ACCESS)
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.build(), TokenClaims.ROLE);
	}

	@Test
	void rejectsARoleThisApplicationDoesNotIssue() {
		assertThatRejects(accessToken().claim(TokenClaims.ROLE, "SUPERUSER").build(), TokenClaims.ROLE);
	}

	/**
	 * Defaulting the kind of token would mean defaulting whether its holder has a tenant,
	 * which is exactly the guess this converter refuses to make.
	 */
	@Test
	void rejectsATokenThatDoesNotSayWhichKindItIs() {
		assertThatRejects(Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(USER_ID)
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.claim(TokenClaims.ROLE, "OWNER")
			.build(), TokenClaims.TOKEN_TYPE);
	}

	@Test
	void rejectsAKindOfTokenThisApplicationDoesNotIssue() {
		assertThatRejects(accessToken().claim(TokenClaims.TOKEN_TYPE, "refresh").build(), TokenClaims.TOKEN_TYPE);
	}

	private void assertThatRejects(Jwt jwt, String claim) {
		assertThatThrownBy(() -> this.converter.convert(jwt)).isInstanceOf(InvalidBearerTokenException.class)
			.hasMessageContaining(claim);
	}

	private Jwt.Builder accessToken() {
		return Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(USER_ID)
			.claim(TokenClaims.TOKEN_TYPE, TokenClaims.ACCESS)
			.claim(TokenClaims.TENANT_ID, TENANT_ID)
			.claim(TokenClaims.EMAIL, "ada@acme.test")
			.claim(TokenClaims.ROLE, "OWNER");
	}

	private Jwt.Builder identityToken() {
		return Jwt.withTokenValue("token")
			.header("alg", "HS256")
			.subject(USER_ID)
			.claim(TokenClaims.TOKEN_TYPE, TokenClaims.IDENTITY)
			.claim(TokenClaims.EMAIL, "ada@acme.test");
	}

}
