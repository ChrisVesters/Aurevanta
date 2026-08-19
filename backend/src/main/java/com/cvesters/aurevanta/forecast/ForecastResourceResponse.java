package com.cvesters.aurevanta.forecast;

import java.util.UUID;

import com.cvesters.aurevanta.resource.Resource;

/**
 * One pool a forecast was scheduled against, as it stood at the moment of the run.
 *
 * <p>
 * <strong>The units are the run's and the name is today's</strong>, which is the same
 * three-way rule a contribution ranking already keeps for the work it names: a pool
 * renamed since is not a thing that moved, so the snapshot holds an identifier and the
 * name comes off the organisation's own list when somebody reads it. One put away since
 * is named and marked; one that no longer exists at all says so, rather than rendering as
 * a blank beside a number.
 *
 * @param units what the pool held <em>then</em>, never what it holds now. That is the
 * whole point of copying the declaration onto the run: somebody who hires a person must
 * not silently change what last month's forecast assumed.
 */
public record ForecastResourceResponse(UUID resourceId, String name, boolean archived, int units) {

	static ForecastResourceResponse of(UUID resourceId, int units, Resource still) {
		return new ForecastResourceResponse(resourceId, (still != null) ? still.getName() : null,
				still != null && still.getArchivedAt() != null, units);
	}

}
