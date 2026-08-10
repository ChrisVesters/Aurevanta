package eu.sonetas.aurevanta.user;

/**
 * What counts as an acceptable password, stated once.
 *
 * <p>
 * Registration and password reset both set a credential, and a bound that differed
 * between them would be worse than an inconsistency: whichever endpoint asked for less
 * would quietly decide the rule for everybody, since anyone can reach the weaker one.
 *
 * <p>
 * Constants rather than configuration, because the upper bound is not ours to choose —
 * bcrypt hashes the first 72 bytes and ignores the rest, so a longer password would store
 * something other than what was typed.
 */
public final class PasswordRules {

	/**
	 * Long enough that a passphrase is the natural answer rather than a mangled word. The
	 * only rule imposed: composition rules push people towards shorter, more predictable
	 * passwords than length alone does.
	 */
	public static final int MINIMUM_LENGTH = 12;

	/** bcrypt's limit, not a preference. */
	public static final int MAXIMUM_LENGTH = 72;

	private PasswordRules() {
	}

}
