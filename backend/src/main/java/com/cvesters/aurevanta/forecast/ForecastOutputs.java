package com.cvesters.aurevanta.forecast;

import java.util.List;

import com.cvesters.aurevanta.forecast.model.Histogram;

/**
 * Everything a forecast answered that is not one of the six numbers with a column of its
 * own.
 *
 * @param histogram the shape of the answer, so the reporting layer can draw a curve
 * without replaying ten thousand runs to find out what one looked like.
 * @param limitations what the model did not do. Stored rather than worked out when the
 * run is read, because two of these describe the engine that produced it: once the
 * common-cause model builds what they name, a run made today has to go on saying it
 * lacked them.
 */
public record ForecastOutputs(Histogram histogram, List<ForecastLimitation> limitations) {
}
