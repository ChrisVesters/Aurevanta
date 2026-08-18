package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The whole account of a movement, read at one confidence.
 *
 * <p>
 * <strong>Three of these rather than one, because the control moves.</strong> All five
 * percentiles come out of the same replays, so publishing the three the confidence
 * control offers costs nothing and keeps the trade immediate — asking again would mean
 * six more simulations to answer a question the reader has already paid for. The other
 * two percentiles have no control and no need, which is the rule {@code DATE_AT} already
 * states.
 *
 * @param from and {@code to} are the two runs' own stored answers at this confidence, so
 * a reader can check the arithmetic: the terms sum to the distance between them exactly.
 */
public record MovementAtResponse(int confidence, LocalDate fromDate, LocalDate toDate, BigDecimal fromHours,
		BigDecimal toHours, List<MovementTermResponse> terms) {

}
