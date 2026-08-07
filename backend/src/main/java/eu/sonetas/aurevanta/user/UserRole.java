package eu.sonetas.aurevanta.user;

/**
 * A person's standing within one organisation. Held on their membership rather than on
 * their account, so the same person can own one organisation and merely belong to
 * another.
 */
public enum UserRole {

	/** Created the organisation, or was invited as one; may administer it. */
	OWNER,

	/** Belongs to the organisation without the right to administer it. */
	MEMBER

}
