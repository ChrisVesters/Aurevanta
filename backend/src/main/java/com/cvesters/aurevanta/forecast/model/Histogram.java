package com.cvesters.aurevanta.forecast.model;

import java.util.List;

/**
 * The shape of a forecast, coarse enough to draw and small enough to keep.
 *
 * <p>
 * Every run that was simulated falls in exactly one bucket, so the counts add up to the
 * sample count. The reporting work draws a curve from this rather than replaying ten
 * thousand runs to find out what one looked like.
 *
 * <p>
 * Counts are a {@code List} rather than an {@code int[]} on purpose: a record with an
 * array component is only nearly immutable, and its {@code equals} compares identities
 * rather than contents — which would quietly break the one test decision 9 rests on, that
 * the same seed produces the same forecast.
 *
 * @param fromHours the earliest run simulated, and the left edge of the first bucket
 * @param toHours the latest, and the right edge of the last. The last bucket includes it,
 * which is why the arithmetic below clamps rather than letting an index run off the end.
 */
public record Histogram(double fromHours, double toHours, List<Integer> counts) {

	public Histogram {
		counts = List.copyOf(counts);
	}

}
