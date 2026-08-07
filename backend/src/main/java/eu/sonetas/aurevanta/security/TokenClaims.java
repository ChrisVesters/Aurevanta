package eu.sonetas.aurevanta.security;

/** Names of the private claims Aurevanta puts in the tokens it issues. */
final class TokenClaims {

	/**
	 * Which of the two kinds of token this is. Required rather than defaulted: guessing
	 * would mean guessing whether the holder has a tenant.
	 */
	static final String TOKEN_TYPE = "token_type";

	/** Scoped to one organisation, and the only kind tenant-owned data is served to. */
	static final String ACCESS = "access";

	/** Names a person but no organisation; good only for choosing one. */
	static final String IDENTITY = "identity";

	static final String TENANT_ID = "tenant_id";

	static final String EMAIL = "email";

	static final String ROLE = "role";

	static final String DISPLAY_NAME = "name";

	private TokenClaims() {
	}

}
