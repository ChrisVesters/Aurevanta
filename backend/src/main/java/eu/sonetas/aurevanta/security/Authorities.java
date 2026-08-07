package eu.sonetas.aurevanta.security;

/**
 * The authorities the two kinds of token grant.
 *
 * <p>
 * Endpoints are guarded on {@link #TENANT_SCOPED} rather than on the absence of
 * {@link #IDENTITY}: a positive requirement keeps a new kind of token from silently
 * inheriting access to everything.
 */
public final class Authorities {

	/** Granted only by an access token, which is pinned to one organisation. */
	public static final String TENANT_SCOPED = "SCOPE_TENANT";

	/** Granted only by an identity token, which names no organisation. */
	public static final String IDENTITY = "SCOPE_IDENTITY";

	/** Spring Security's convention for authorities that stand for a role. */
	static final String ROLE_PREFIX = "ROLE_";

	private Authorities() {
	}

}
