package com.cvesters.aurevanta.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the mail this application sends. The transport itself is configured under
 * {@code spring.mail.*}, so pointing at a provider is configuration rather than code.
 *
 * @param from the address every message leaves from
 * @param baseUrl origin that links in mail are built against. The backend cannot discover
 * its own public URL — behind a proxy it sees an internal host and port — and a link
 * built from the wrong one is a link nobody can follow, so it is stated rather than
 * guessed. Points at the frontend, which is what a recipient clicking through should
 * reach.
 */
@ConfigurationProperties("aurevanta.mail")
public record MailProperties(String from, String baseUrl) {

	public MailProperties {
		if (from == null || from.isBlank()) {
			from = "no-reply@localhost";
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "http://localhost:5173";
		}
	}

	/**
	 * Builds an absolute link for a recipient to follow. Tolerates a base URL with or
	 * without a trailing slash, because both are natural to write in configuration and a
	 * doubled slash in an emailed link looks broken even where it still resolves.
	 */
	public String link(String path) {
		String origin = this.baseUrl.endsWith("/") ? this.baseUrl.substring(0, this.baseUrl.length() - 1)
				: this.baseUrl;
		return path.startsWith("/") ? origin + path : origin + "/" + path;
	}

}
