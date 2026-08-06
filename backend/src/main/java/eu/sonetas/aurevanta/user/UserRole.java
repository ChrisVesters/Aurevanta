package eu.sonetas.aurevanta.user;

/** A user's standing within their own tenant. */
public enum UserRole {

	/** Created the tenant by registering; may administer it. */
	OWNER,

	/** Joined an existing tenant. */
	MEMBER

}
