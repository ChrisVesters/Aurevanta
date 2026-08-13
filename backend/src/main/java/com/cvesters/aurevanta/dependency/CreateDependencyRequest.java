package com.cvesters.aurevanta.dependency;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * One arrow: this has to finish before that begins, and how long afterwards.
 *
 * <p>
 * <strong>Both ends are named in the body and neither in the path.</strong> An edge has
 * two ends of equal standing, so putting one of them in the URL would make the pair read
 * as though one owned the other — and would leave the server checking a second identifier
 * against the first, which is the thing {@code WorkItemController} avoids by addressing
 * an item on its own. Which plan the edge belongs to is not asked either: the items
 * answer it, and a third identifier that could disagree with them would be a refusal
 * nobody could act on.
 *
 * <p>
 * {@code lagHours} is required rather than defaulted, even though zero is the ordinary
 * answer. Jackson cannot tell an absent field from a null one, so a server that filled it
 * in would be guessing at the one number this row exists to carry — and zero is not a
 * guess here, it is a claim that there is no wait, which is worth having somebody make.
 *
 * <p>
 * Zero is allowed and a negative is not. A lead — a successor starting before its
 * predecessor finishes — is a different kind of edge rather than a small lag, and
 * decision 4 models one kind. {@code @Digits} matches the column exactly, so a hundredth
 * of an hour cannot pass validation and then be rounded away on the way into
 * {@code numeric(12, 2)}.
 */
public record CreateDependencyRequest(@NotNull UUID predecessorItemId, @NotNull UUID successorItemId,

		@NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal lagHours) {

}
