package eu.sonetas.aurevanta.ratelimit;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the one limiter callers inject. The two windows behind it are an implementation
 * detail: a caller says who is asking and who would be written to, and is told whether
 * that may proceed.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfiguration {

	@Bean
	MailRateLimiter mailRateLimiter(RateLimitProperties properties, Clock clock) {
		return new MailRateLimiter(new RateLimiter(properties.perIp(), properties.ipWindow(), clock),
				new RateLimiter(properties.perAddress(), properties.addressWindow(), clock));
	}

	/**
	 * Separate counts from the mail limiter's, not shared ones. Guessing at passwords and
	 * asking for confirmation links are different things done by different people, and
	 * one budget would let either exhaust the other.
	 */
	@Bean
	SignInRateLimiter signInRateLimiter(RateLimitProperties properties, Clock clock) {
		return new SignInRateLimiter(new RateLimiter(properties.signInPerIp(), properties.signInWindow(), clock),
				new RateLimiter(properties.signInPerAccount(), properties.signInWindow(), clock));
	}

}
