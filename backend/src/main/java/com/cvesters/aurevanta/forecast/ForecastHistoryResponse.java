package com.cvesters.aurevanta.forecast;

import java.util.List;

/**
 * Every forecast of one plan, and what the sequence of them says.
 *
 * <p>
 * <strong>A list wrapped in an object, which is a change to a shape somebody was already
 * reading.</strong> The verdict belongs to the history rather than to any run in it, so
 * there is nowhere on a run to put it — and a second request for one flag would be a
 * screen that renders its history before it can say the one thing about it worth reading
 * out loud.
 *
 * @param runs newest first, exactly as before.
 * @param drift null only for a plan nobody has forecast yet.
 */
public record ForecastHistoryResponse(List<ForecastResponse> runs, DriftResponse drift) {

}
