package eu.sonetas.aurevanta.security;

/** Names of the private claims Aurevanta puts in its access tokens. */
final class TokenClaims {

	static final String TENANT_ID = "tenant_id";

	static final String EMAIL = "email";

	static final String ROLE = "role";

	static final String DISPLAY_NAME = "name";

	private TokenClaims() {
	}

}
