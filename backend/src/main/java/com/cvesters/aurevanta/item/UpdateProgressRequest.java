package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * What has happened to a piece of work.
 *
 * <p>
 * The dates come from the caller rather than from the server's clock, which is the only
 * way this can record something that already happened: work is marked finished on the
 * Monday after it finished at least as often as on the day. What the server will not do
 * is invent one — a state that needs a date and did not get one is refused, so nothing in
 * the record is a guess the person who filled the form in never saw.
 *
 * @param actualEffortHours optional in every state, including {@code DONE}. Most teams do
 * not track it, and refusing to let somebody mark an item finished because they cannot
 * say how long it took would refuse the common case in order to serve M8.
 */
public record UpdateProgressRequest(@NotNull WorkItemStatus status,

		LocalDate startedOn,

		LocalDate completedOn,

		@Positive @Digits(integer = 10, fraction = 2) BigDecimal actualEffortHours) {

}
