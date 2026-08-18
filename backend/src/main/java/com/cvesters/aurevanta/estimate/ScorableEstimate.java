package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One range somebody wrote down, beside what the work it describes actually took.
 *
 * <p>
 * A projection rather than an entity, constructed in JPQL the way {@code ProjectCount}
 * is: calibration reads every estimate an organisation has ever made against finished
 * work, and loading that as a graph would fetch estimators, items and projects nothing is
 * going to ask about.
 *
 * <p>
 * <strong>The stated middle is not here, and that is step 2's decision arriving in the
 * query.</strong> The band being scored is P10 to P90, the fit takes the two ends, and
 * whether the middle agrees with them is {@code EstimateQuality}'s question rather than
 * this one. Carrying it would be a column read on every row so that nothing could use it.
 *
 * @param actualHours the item's, repeated on each of its estimates. Two people who
 * estimated one task are scored against one outcome, and it is cheaper to repeat it here
 * than to look the item up again per row.
 * @param createdAt what decides which bucket this lands in, and the reason a revision is
 * a new row rather than an edit.
 */
public record ScorableEstimate(UUID itemId, UUID estimatorId, BigDecimal p10Hours, BigDecimal p90Hours,
		Instant createdAt, BigDecimal actualHours) {
}
