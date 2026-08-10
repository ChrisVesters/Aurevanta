package com.cvesters.aurevanta.mail;

/**
 * The way out of the application for email. Everything that sends mail depends on this
 * and never on a transport, so moving from SMTP to a provider's HTTP API later is one new
 * implementation and no change to any caller.
 *
 * <p>
 * <strong>Sending says nothing about delivery.</strong> The bean wired into the context
 * hands the message to a background thread and returns, so a slow or unreachable server
 * cannot add seconds to the request that triggered it. A caller therefore learns neither
 * that the message was accepted nor that it failed — which is deliberate: registration
 * must not fail because a mail server is down.
 */
@FunctionalInterface
public interface EmailSender {

	void send(EmailMessage message);

}
