package eu.sonetas.aurevanta;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AurevantaApplication {

	public static void main(String[] args) {
		SpringApplication.run(AurevantaApplication.class, args);
	}

	/** Injected rather than called statically so tests can fix the current time. */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
