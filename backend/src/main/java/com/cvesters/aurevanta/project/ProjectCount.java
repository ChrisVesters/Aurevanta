package com.cvesters.aurevanta.project;

import java.util.UUID;

/**
 * How many of something one project has, as a grouped count comes back from the database.
 *
 * <p>
 * Exists so the coverage queries can say what they mean in a constructor expression
 * rather than handing back {@code Object[]} for the service to index into by position —
 * which is the shape that silently swaps two columns the day somebody reorders the
 * select.
 */
public record ProjectCount(UUID projectId, long count) {

}
