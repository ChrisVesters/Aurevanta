package com.cvesters.aurevanta.requirement;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.cvesters.aurevanta.resource.ResourceLimits;

/**
 * What a piece of work needs, in place of whatever it needed before.
 *
 * @param needs at most one line per pool, and an empty list is a claim rather than an
 * omission: it says this is generic work anybody can pick up, which the scheduler reads
 * as one unit of whichever pool has one free. The bound is the same order as the plan
 * itself — a task that names more distinct pools than a team has kinds of thing is a
 * mistake, not a requirement.
 */
public record SetRequirementsRequest(@NotNull @Size(max = 50) List<@Valid RequiredResource> needs) {

	/**
	 * One line of it: a pool, and how many of it the work ties up.
	 *
	 * @param units occupancy and never speed — two units means the item holds two, not
	 * that it goes twice as fast. {@code Requirement} carries the argument.
	 */
	public record RequiredResource(@NotNull UUID resourceId,

			@NotNull @Positive @Max(ResourceLimits.MOST_UNITS) Integer units) {
	}

}
