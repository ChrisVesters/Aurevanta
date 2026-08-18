package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One claim about a piece of work, as the API describes it.
 *
 * <p>
 * Carries the reporter's name as well as their identifier, the way an estimate does:
 * server prose is never shown to anybody, but a person's own display name is not prose,
 * and the alternative is a screen that has to hold the member list to say who said
 * something.
 *
 * @param reportedAt the moment the server heard it, unlike the two dates, which are days
 * somebody reported. Both kinds are on one row here and the distinction is worth keeping
 * in view: only one of them is a thing this server observed.
 */
public record ProgressReportResponse(UUID id, UUID itemId, UUID reportedById, String reportedByName, Instant reportedAt,
		WorkItemStatus status, LocalDate startedOn, LocalDate completedOn, BigDecimal actualEffortHours) {

	public static ProgressReportResponse of(WorkItemProgress report) {
		return new ProgressReportResponse(report.getId(), report.getWorkItem().getId(), report.getReportedBy().getId(),
				report.getReportedBy().getDisplayName(), report.getReportedAt(), report.getStatus(),
				report.getStartedOn(), report.getCompletedOn(), report.getActualEffortHours());
	}

}
