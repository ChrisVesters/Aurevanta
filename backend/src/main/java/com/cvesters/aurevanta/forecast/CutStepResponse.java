package com.cvesters.aurevanta.forecast;

import java.util.UUID;

/**
 * One thing dropped, and where the plan stands once it and everything before it is gone.
 *
 * <p>
 * <strong>The confidence here was measured with every earlier step already cut</strong>,
 * not added up from what each was worth alone. Two cuts on one chain overlap and the
 * second buys almost nothing; two on separate branches leave the later one deciding. This
 * is the number a person may act on, and the singles beside it are the shortlist that
 * produced it.
 *
 * @param confidence where the plan stands after this cut and every one above it, as a
 * percentage.
 */
public record CutStepResponse(UUID itemId, String title, boolean archived, double confidence) {

}
