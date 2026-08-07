package eu.sonetas.aurevanta.mail;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the one {@link EmailSender} the application injects: SMTP delivery, wrapped so it
 * happens off the request thread. Callers see only the port, so neither the transport nor
 * the threading is theirs to know about.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailProperties.class)
class MailConfiguration {

	private static final Logger log = LoggerFactory.getLogger(MailConfiguration.class);

	@Bean
	EmailSender emailSender(JavaMailSender transport, MailProperties properties,
			@Qualifier("mailExecutor") Executor mailExecutor, @Value("${spring.mail.host:}") String smtpHost) {
		warnAboutDevelopmentDefaults(smtpHost, properties.baseUrl());
		return new AsyncEmailSender(new SmtpEmailSender(transport, properties), mailExecutor);
	}

	/**
	 * Says so at startup when mail is still pointed at a developer machine.
	 *
	 * <p>
	 * Delivery failure is deliberately logged rather than thrown, which means an
	 * unconfigured mail server is silent — and from Step 5, where an unverified address
	 * cannot sign in, silence means nobody can reach their account and nothing says why.
	 * The signing key warns about the same class of mistake for the same reason.
	 */
	static void warnAboutDevelopmentDefaults(String smtpHost, String baseUrl) {
		if (pointsAtThisMachine(smtpHost)) {
			log.warn("spring.mail.host is '{}', so mail goes to a local capture and reaches nobody. "
					+ "Point it at a real SMTP server anywhere but a developer machine.", smtpHost);
		}
		if (pointsAtThisMachine(baseUrl)) {
			log.warn("aurevanta.mail.base-url is '{}', so links in mail point at a developer machine "
					+ "and will not work for anyone who receives them.", baseUrl);
		}
	}

	private static boolean pointsAtThisMachine(String value) {
		return value == null || value.isBlank() || value.contains("localhost") || value.contains("127.0.0.1")
				|| value.contains("[::1]");
	}

	/**
	 * A small pool of its own rather than the application's shared executor: JavaMail
	 * opens a connection per send and a hung one would otherwise tie up threads other
	 * work needs. The queue is bounded, so a mail server that stops responding eventually
	 * refuses new messages and logs them instead of growing without limit.
	 */
	@Bean
	ThreadPoolTaskExecutor mailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setThreadNamePrefix("mail-");
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		// Mail already in flight at shutdown is worth the short wait; a verification link
		// that was never sent leaves someone unable to finish signing up. Shutdown itself
		// needs no destroyMethod: ThreadPoolTaskExecutor is a DisposableBean.
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(5);
		return executor;
	}

}
