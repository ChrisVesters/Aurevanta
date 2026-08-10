package com.cvesters.aurevanta.token;

import java.time.Instant;

/**
 * A freshly issued token, and the only time its raw value exists outside the recipient's
 * inbox. Nothing stores it: the database keeps a hash, so a value not put into an email
 * here is lost for good.
 *
 * @param value the raw token, to be embedded in a link
 * @param expiresAt when redemption stops working, so a caller can say so in the mail
 */
public record SingleUseToken(String value, Instant expiresAt) {
}
