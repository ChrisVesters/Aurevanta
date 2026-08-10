package com.cvesters.aurevanta.mail;

/**
 * One outbound message, in plain text.
 *
 * <p>
 * No HTML and no sender: the sender is configuration, not something a caller decides, so
 * every message leaves from the same address without each caller having to know it.
 *
 * @param to the recipient's address
 * @param subject a single line, no trailing newline
 * @param body plain text; wrap it yourself, nothing reflows it
 */
public record EmailMessage(String to, String subject, String body) {
}
