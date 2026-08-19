package com.cvesters.aurevanta.resource;

/**
 * What a pool may hold, stated once because two requests ask it.
 *
 * <p>
 * The bound is on absurdity rather than on ambition, which is
 * {@code Engine.MAX_SAMPLE_COUNT}'s shape: nothing about the scheduler cares whether a
 * pool holds ten units or ten thousand, but a plan whose capacity is a mistyped year is a
 * forecast that finishes on its first afternoon, and the number is small enough to see.
 */
public final class ResourceLimits {

	/** More people than any team this product's 500-item scale target describes. */
	public static final int MOST_UNITS = 1_000;

	private ResourceLimits() {
	}

}
