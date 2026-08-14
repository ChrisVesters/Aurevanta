package com.cvesters.aurevanta.forecast;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * How a forecast's inputs and outputs are written down, and read back years later.
 *
 * <p>
 * <strong>Its own mapper, deliberately not the application's.</strong> The one Spring
 * builds is configuration: a property-naming strategy or an inclusion rule, set for the
 * sake of the API, would silently change the shape of everything written from that moment
 * on and stop the rows written before it from being readable at all. This table exists to
 * be replayed long after anybody remembers what was in {@code application.yaml}, so its
 * format is pinned here, where changing it is a decision rather than a side effect.
 *
 * <p>
 * Nothing here is configured, and that <em>is</em> the configuration: Jackson's defaults
 * for records, {@code UUID}s, {@code BigDecimal}s and enum names are stable, and every
 * one of them round-trips as itself.
 *
 * <p>
 * Neither method catches anything. A snapshot this application wrote, with this mapper,
 * into a column it owns, cannot fail to be read back — so a failure here is a corrupted
 * or hand-edited row rather than a case with an answer, and turning it into a problem
 * document would be inventing a refusal no request can produce.
 */
final class ForecastSnapshots {

	private static final ObjectMapper JSON = JsonMapper.builder().build();

	private ForecastSnapshots() {
	}

	static String write(Object document) {
		return JSON.writeValueAsString(document);
	}

	static <T> T read(String document, Class<T> type) {
		return JSON.readValue(document, type);
	}

}
