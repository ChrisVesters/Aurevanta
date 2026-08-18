package com.cvesters.aurevanta.item;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The earliest day work on one item was ever claimed to have begun.
 *
 * <p>
 * A projection rather than an entity, constructed in JPQL the way {@code ProjectCount}
 * is, because two grouped queries answer this and neither returns a row anybody wants
 * loaded.
 *
 * <p>
 * <strong>Earliest, because that is the unflattering direction.</strong> Two claims about
 * when work began means the earlier one wins: moving a start date later is what turns a
 * report into a forecast in M8's exclusion rule, so the rule reads the first thing
 * anybody said rather than the last.
 *
 * @param startedOn never null — an item nobody has claimed a start for has no row here at
 * all, which is a different answer from a start of nothing.
 */
public record ReportedStart(UUID itemId, LocalDate startedOn) {
}
