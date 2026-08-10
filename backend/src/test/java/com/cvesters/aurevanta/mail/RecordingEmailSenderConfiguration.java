package com.cvesters.aurevanta.mail;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stands the whole mail port down to {@link RecordingEmailSender}, wrapper included, so a
 * test can read what would have gone out and does so immediately rather than waiting on a
 * background thread.
 *
 * <p>
 * Import this anywhere a test drives something that sends mail. The SMTP adapter is
 * proved separately, once, by {@code SmtpEmailSenderTests}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingEmailSenderConfiguration {

	@Bean
	@Primary
	public RecordingEmailSender recordingEmailSender() {
		return new RecordingEmailSender();
	}

}
