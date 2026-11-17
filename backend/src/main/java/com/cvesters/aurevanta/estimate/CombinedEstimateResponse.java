package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.cvesters.aurevanta.forecast.model.EstimatorMixture;
import com.cvesters.aurevanta.forecast.model.LogNormalFit;

/**
 * What everybody's current ranges on one piece of work say together.
 *
 * <p>
 * <strong>Derived, never stored, and never an estimate.</strong> It is the distribution
 * the forecast samples the work from — see {@link EstimatorMixture} — read at the three
 * points the estimate form asks about. Kept off {@code estimates}, because a row there
 * would be an estimator nobody is: the forecast would sample it beside the people it was
 * made from, and calibration would score it. Kept off {@code work_items}, because a
 * stored copy would go stale on the next estimate and freeze today's fit into rows a
 * future engine version reads differently.
 *
 * @param estimators how many people's current ranges went into it; the screen shows a
 * combination only where there is more than one, since one person's range is already on
 * it
 * @param range the three readings, or {@code null} where nobody has estimated the work —
 * one nullable object rather than three nullable numbers, so there is no shape holding
 * some of them
 */
public record CombinedEstimateResponse(int estimators, Range range) {

	/** Hours keep two decimal places in the column, so they keep two here. */
	private static final int SCALE = 2;

	public record Range(BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p90Hours) {
	}

	static CombinedEstimateResponse of(List<Estimate> current) {
		if (current.isEmpty()) {
			return new CombinedEstimateResponse(0, null);
		}
		// The two ends only, which is what the forecast fits as well.
		EstimatorMixture mixture = EstimatorMixture.of(current.stream()
			.map((estimate) -> LogNormalFit.from(estimate.getP10Hours().doubleValue(),
					estimate.getP90Hours().doubleValue()))
			.toList());
		return new CombinedEstimateResponse(current.size(),
				new Range(hours(mixture.quantile(0.1)), hours(mixture.quantile(0.5)), hours(mixture.quantile(0.9))));
	}

	private static BigDecimal hours(double value) {
		return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP);
	}

}
