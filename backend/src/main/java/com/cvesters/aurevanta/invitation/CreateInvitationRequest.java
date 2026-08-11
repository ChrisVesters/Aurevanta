package com.cvesters.aurevanta.invitation;

import com.cvesters.aurevanta.user.UserRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Who to invite, and what they will be once they accept.
 *
 * <p>
 * The organisation is deliberately absent: it comes from the caller's own access token,
 * which is the only source that cannot be pointed at somebody else's.
 *
 * @param email where the invitation is sent, checked for shape so that an owner who
 * mistypes an address is told so rather than left waiting for an acceptance
 * @param role stated rather than defaulted — inviting a second owner and inviting a
 * member are different enough decisions that neither should happen by omission
 */
public record CreateInvitationRequest(@NotBlank @Email @Size(max = 320) String email, @NotNull UserRole role) {

	/**
	 * Stripped before validation, as every request record here is: {@code @Email} rejects
	 * a padded address outright, so trimming afterwards would answer an address pasted
	 * out of a directory with "enter a valid email address".
	 */
	public CreateInvitationRequest {
		email = (email != null) ? email.strip() : null;
	}

}
