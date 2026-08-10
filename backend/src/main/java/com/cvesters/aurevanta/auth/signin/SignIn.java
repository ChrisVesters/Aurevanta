package com.cvesters.aurevanta.auth.signin;

import java.util.List;

import com.cvesters.aurevanta.membership.Membership;
import com.cvesters.aurevanta.user.User;

/**
 * What checking a password resolved to. Sealed so the web layer has to answer for every
 * case: an identity with no memberships is as real an outcome as one with several, and
 * neither may fall through to a session.
 */
public sealed interface SignIn {

	/** Exactly one membership, so the caller can be given a session outright. */
	record IntoOrganisation(Membership membership) implements SignIn {
	}

	/** Nothing to sign into yet: the caller gets an identity token and the list. */
	record ChooseOrganisation(User user, List<Membership> memberships) implements SignIn {
	}

}
