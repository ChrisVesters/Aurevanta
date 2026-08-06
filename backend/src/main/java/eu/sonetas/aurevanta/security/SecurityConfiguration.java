package eu.sonetas.aurevanta.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			// Authentication travels in an Authorization header that a cross-site request
			// cannot set, and no session cookie is issued, so there is nothing for CSRF
			// protection to defend.
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement((session) -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(
					(requests) -> requests.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login")
						.permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
						.permitAll()
						.anyRequest()
						.authenticated())
			.oauth2ResourceServer((resourceServer) -> resourceServer
				.jwt((jwt) -> jwt.jwtAuthenticationConverter(new AuthenticatedUserJwtConverter())))
			.build();
	}

	/**
	 * Delegating encoder: hashes with bcrypt and stores the algorithm alongside the hash,
	 * so stored credentials can be migrated to a stronger algorithm later without
	 * invalidating existing ones.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
