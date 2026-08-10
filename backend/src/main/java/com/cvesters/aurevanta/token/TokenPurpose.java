package com.cvesters.aurevanta.token;

/**
 * What a single-use token entitles its holder to do.
 *
 * <p>
 * Redemption checks the purpose as well as the hash, so a token issued for one thing
 * cannot be spent on another. That matters because the two below are not equally
 * powerful: a confirmation link proves someone reads an inbox, while a reset link lets
 * them take the account over.
 */
public enum TokenPurpose {

	/** Proves the holder can read the address the account was registered with. */
	EMAIL_VERIFICATION,

	/** Lets the holder choose a new password without knowing the old one. */
	PASSWORD_RESET

}
