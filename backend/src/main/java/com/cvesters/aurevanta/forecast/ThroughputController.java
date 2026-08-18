package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cvesters.aurevanta.security.AuthenticatedUser;

/**
 * What a plan's own history says, beside what its estimates say.
 *
 * <p>
 * <strong>Its own controller rather than a sixth method on
 * {@code ForecastController}</strong>, because the two are not the same kind of thing.
 * That one creates and reads runs of the engine — rows in {@code forecast_runs} that M10
 * walks as a history of somebody deliberately re-forecasting. This creates nothing and
 * reads no run: it counts what a team finished and projects what it has left, and the
 * only estimate involved is the absence of one.
 *
 * <p>
 * <strong>A {@code GET}, unlike every forecast endpoint before it.</strong> It writes
 * nothing and takes one date. `POST /api/projects/{id}/forecasts` exists because a run is
 * a row; here the history is already stored, already dated and already required on every
 * finished item, so a row would be a cached answer to a question that is cheap to ask
 * again.
 *
 * <p>
 * Reachable by every member, with the organisation from the caller's token as everywhere
 * else.
 */
@RestController
class ThroughputController {

	private final ThroughputService throughput;

	ThroughputController(ThroughputService throughput) {
		this.throughput = throughput;
	}

	/**
	 * @param asOf required, because a server picking "today" would pick its own
	 * timezone's today — the argument {@code todayHere} makes on the other side of the
	 * wire, and the one a forecast run's {@code starts_on} already makes.
	 */
	@GetMapping("/api/projects/{projectId}/throughput")
	ThroughputResponse read(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
		return this.throughput.forecastFor(caller.userId(), caller.tenantId(), projectId, asOf);
	}

}
