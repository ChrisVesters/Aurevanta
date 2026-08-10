package com.cvesters.aurevanta.mail;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.cvesters.aurevanta.TestcontainersConfiguration;
import com.cvesters.aurevanta.mail.AsyncEmailSender;
import com.cvesters.aurevanta.mail.EmailSender;
import com.cvesters.aurevanta.mail.MailConfiguration;
import com.cvesters.aurevanta.mail.MailProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the application actually injects. The pieces are unit-tested separately; what this
 * pins down is that they are assembled the right way round — a context that handed out
 * the bare SMTP adapter would put a mail server on the request path and every test below
 * this level would still pass.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MailConfigurationTests {

	@Autowired
	private EmailSender emailSender;

	@Autowired
	private MailProperties properties;

	private ListAppender<ILoggingEvent> logged;

	private Logger logger;

	@BeforeEach
	void captureTheLog() {
		this.logged = new ListAppender<>();
		this.logged.start();
		this.logger = (Logger) LoggerFactory.getLogger(MailConfiguration.class);
		this.logger.addAppender(this.logged);
	}

	@AfterEach
	void releaseTheLog() {
		this.logger.detachAppender(this.logged);
	}

	@Test
	void injectsASenderThatDeliversOffTheRequestThread() {
		assertThat(this.emailSender).isInstanceOf(AsyncEmailSender.class);
	}

	/**
	 * Asserts that the origin is bound and composed with, rather than what it happens to
	 * be: pinning the development value here would make editing application.properties
	 * break a test that is not about the value.
	 */
	@Test
	void bindsAnOriginThatLinksAreBuiltFrom() {
		assertThat(this.properties.baseUrl()).isNotBlank();
		assertThat(this.properties.from()).isNotBlank();
		assertThat(this.properties.link("/verify-email")).isEqualTo(this.properties.baseUrl() + "/verify-email");
	}

	/**
	 * Delivery failure is logged rather than thrown, so an unconfigured mail server is
	 * otherwise completely silent — and from Step 5 that means nobody can reach their
	 * account and nothing says why.
	 */
	@Test
	void saysSoWhenMailIsStillPointedAtADeveloperMachine() {
		MailConfiguration.warnAboutDevelopmentDefaults("localhost", "http://localhost:5173");

		assertThat(warnings()).hasSize(2);
		assertThat(warnings().getFirst().getFormattedMessage()).contains("spring.mail.host");
		assertThat(warnings().getLast().getFormattedMessage()).contains("aurevanta.mail.base-url");
	}

	@Test
	void saysSoWhenNoMailHostIsConfiguredAtAll() {
		MailConfiguration.warnAboutDevelopmentDefaults("", "https://app.acme.test");

		assertThat(warnings()).singleElement()
			.satisfies((event) -> assertThat(event.getFormattedMessage()).contains("spring.mail.host"));
	}

	/** The same machine can be named several ways, and each one is still a dead end. */
	@Test
	void recognisesEveryWayOfNamingThisMachine() {
		MailConfiguration.warnAboutDevelopmentDefaults("127.0.0.1", "http://[::1]:5173");
		MailConfiguration.warnAboutDevelopmentDefaults(null, "https://app.acme.test");

		assertThat(warnings()).hasSize(3);
	}

	@Test
	void staysQuietWhenMailIsConfiguredForReal() {
		MailConfiguration.warnAboutDevelopmentDefaults("smtp.eu.mailgun.org", "https://app.acme.test");

		assertThat(warnings()).isEmpty();
	}

	private List<ILoggingEvent> warnings() {
		return this.logged.list.stream().filter((event) -> event.getLevel() == Level.WARN).toList();
	}

}
