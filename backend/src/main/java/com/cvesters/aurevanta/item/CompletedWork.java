package com.cvesters.aurevanta.item;

/**
 * How much of an organisation's finished work says how long it took.
 *
 * <p>
 * The difference between the two is the single biggest reason a calibration record is
 * empty, and it is worth a number rather than a shrug: {@code actual_effort_hours} is
 * optional in every state on purpose, so most teams finish a great deal of work without
 * ever answering the one question calibration reads.
 *
 * @param completed every item reported as done, archived ones included — putting work
 * away is not a way to leave a record.
 * @param withActual how many of those recorded what they took.
 */
public record CompletedWork(long completed, long withActual) {
}
