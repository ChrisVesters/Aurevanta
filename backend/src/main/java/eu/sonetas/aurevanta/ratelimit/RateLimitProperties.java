package eu.sonetas.aurevanta.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How much mail one address or one source may provoke.
 *
 * <p>
 * Two limits rather than one, because they stop different things. The per-address limit
 * protects a <em>recipient</em> from being buried in messages they never asked for; the
 * per-source limit protects everyone else from one client spraying single messages at a
 * great many addresses, which the per-address limit cannot see.
 *
 * @param perAddress messages one email address may provoke within {@code addressWindow}
 * @param addressWindow how far back the per-address count reaches
 * @param perIp requests one source address may make within {@code ipWindow}, across every
 * endpoint that sends mail
 * @param ipWindow how far back the per-source count reaches
 */
@ConfigurationProperties("aurevanta.rate-limit")
public record RateLimitProperties(int perAddress, Duration addressWindow, int perIp, Duration ipWindow) {

	/**
	 * Asking three times in a quarter of an hour is already someone who has decided the
	 * mail is not coming; a fourth message will not change that, and each one is a
	 * message somebody else may not have asked for at all.
	 */
	private static final int DEFAULT_PER_ADDRESS = 3;

	/**
	 * Deliberately far looser than the per-address limit, because a source address is a
	 * much worse proxy for a person: an office, a university or a mobile network shares
	 * one, and this limit is the one that would refuse them all together.
	 */
	private static final int DEFAULT_PER_IP = 20;

	private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(15);

	public RateLimitProperties {
		// Boot binds an absent int to zero, which as a limit would refuse everything —
		// including the first request anybody makes. Treat it as "unset" rather than as a
		// deliberate lockout, since no configuration is the ordinary case.
		if (perAddress <= 0) {
			perAddress = DEFAULT_PER_ADDRESS;
		}
		if (perIp <= 0) {
			perIp = DEFAULT_PER_IP;
		}
		if (addressWindow == null) {
			addressWindow = DEFAULT_WINDOW;
		}
		if (ipWindow == null) {
			ipWindow = DEFAULT_WINDOW;
		}
	}

}
