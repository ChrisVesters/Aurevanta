package com.cvesters.aurevanta.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.cvesters.aurevanta.mail.AsyncEmailSender;
import com.cvesters.aurevanta.mail.EmailSender;

/**
 * A mail server that refuses everything, behind the <em>real</em> asynchronous wrapper.
 *
 * <p>
 * The distinction matters: {@link RecordingEmailSenderConfiguration} replaces the whole
 * port, wrapper included, so a test using it would never exercise the thing that swallows
 * delivery failures. Anything asserting that a request survives a broken mail server has
 * to go through the wrapper that actually makes that true.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FailingEmailSenderConfiguration {

	@Bean
	@Primary
	public EmailSender failingEmailSender() {
		EmailSender refuses = (message) -> {
			throw new IllegalStateException("the mail server is down");
		};
		// Inline, so the failure happens before the assertion rather than on a timer.
		return new AsyncEmailSender(refuses, Runnable::run);
	}

}
