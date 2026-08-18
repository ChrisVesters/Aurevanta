package com.cvesters.aurevanta.item;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One claim somebody made about how far along a piece of work is, kept exactly as they
 * made it.
 *
 * <p>
 * <strong>Nothing here can be changed.</strong> There is no setter, no {@code updatedAt}
 * and no endpoint that rewrites a row — the same shape
 * {@link com.cvesters.aurevanta.estimate.Estimate} has, for the same reason. A second
 * report is a second row and the first stays where it is.
 *
 * <p>
 * <strong>This sits beside the four columns on {@link WorkItem} rather than instead of
 * them.</strong> The item keeps its latest state, because that is what a screen draws and
 * what a forecast reads; this holds the history, because that is what M8 measures against
 * and what nothing could reconstruct later. Both are written in one transaction, so they
 * cannot come to disagree about what was last said.
 *
 * <p>
 * <strong>What it exists for is one rule.</strong> M8 refuses to score an estimate
 * written after the work began, comparing an estimate's immutable {@code created_at}
 * against the day work started — and until this table existed that day could be moved
 * afterwards by anybody, with no trace. The hit rate is the one number in this product
 * whose whole value is that it is unflattering, and it must not be improvable by editing
 * the date it is measured against.
 */
@Entity
@Table(name = "work_item_progress")
public class WorkItemProgress {

	@Id
	@GeneratedValue
	private UUID id;

	/**
	 * Written and never read back through this class: every query that reads a report is
	 * already scoped by it, which is what the column is for.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_item_id", nullable = false)
	private WorkItem workItem;

	/**
	 * The person, not their membership — following the estimator, and for the same
	 * reason: somebody who leaves the organisation still said this.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "reported_by_user_id", nullable = false)
	private User reportedBy;

	/**
	 * When the server heard the claim, and so a moment rather than a day. The two dates
	 * below are days somebody reported; this is the one field here the server observed.
	 */
	@Column(name = "reported_at", nullable = false)
	private Instant reportedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private WorkItemStatus status;

	@Column(name = "started_on")
	private LocalDate startedOn;

	@Column(name = "completed_on")
	private LocalDate completedOn;

	@Column(name = "actual_effort_hours", precision = 12, scale = 2)
	private BigDecimal actualEffortHours;

	protected WorkItemProgress() {
		// for JPA
	}

	public WorkItemProgress(WorkItem workItem, User reportedBy, WorkItemStatus status, LocalDate startedOn,
			LocalDate completedOn, BigDecimal actualEffortHours, Instant reportedAt) {
		// Taken from the item rather than from a caller, so a report cannot be filed
		// under one organisation and against another's work.
		this.tenant = workItem.getTenant();
		this.workItem = workItem;
		this.reportedBy = reportedBy;
		this.status = status;
		this.startedOn = startedOn;
		this.completedOn = completedOn;
		this.actualEffortHours = actualEffortHours;
		this.reportedAt = reportedAt;
	}

	public UUID getId() {
		return id;
	}

	public WorkItem getWorkItem() {
		return workItem;
	}

	public User getReportedBy() {
		return reportedBy;
	}

	public Instant getReportedAt() {
		return reportedAt;
	}

	public WorkItemStatus getStatus() {
		return status;
	}

	public LocalDate getStartedOn() {
		return startedOn;
	}

	public LocalDate getCompletedOn() {
		return completedOn;
	}

	public BigDecimal getActualEffortHours() {
		return actualEffortHours;
	}

}
