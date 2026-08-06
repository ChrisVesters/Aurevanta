package eu.sonetas.aurevanta.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication whose principal is an {@link AuthenticatedUser}, so controllers can take
 * one directly with {@code @AuthenticationPrincipal}.
 */
public final class AurevantaAuthenticationToken extends AbstractAuthenticationToken {

	private final transient AuthenticatedUser principal;

	private final transient Jwt credentials;

	AurevantaAuthenticationToken(AuthenticatedUser principal, Jwt credentials,
			Collection<? extends GrantedAuthority> authorities) {
		super(authorities);
		this.principal = principal;
		this.credentials = credentials;
		setAuthenticated(true);
	}

	@Override
	public AuthenticatedUser getPrincipal() {
		return this.principal;
	}

	@Override
	public Jwt getCredentials() {
		return this.credentials;
	}

	@Override
	public String getName() {
		return this.principal.email();
	}

}
