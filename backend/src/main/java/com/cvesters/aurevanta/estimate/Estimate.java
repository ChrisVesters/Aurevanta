package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.tenant.Tenant;
import com.cvesters.aurevanta.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One person's three-point range for one piece of work, as they saw it at one moment.
 *
 * <p>
 * <strong>Nothing here can be changed.</strong> There is no setter, no {@code updatedAt}
 * and no endpoint that rewrites a row: a revision is a new row, and both stay readable.
 * M8 measures how often somebody's P10–P90 band contained the truth, which is a question
 * about what they said at the time — an update would answer it with what they think now
 * and destroy the evidence for the old answer at the same moment.
 *
 * <p>
 * That immutability is doing authorization work as well, which is worth noticing: every
 * member may write estimates, and none of them can rewrite a colleague's, because nothing
 * can rewrite any.
 */
@Entity
@Table(name = "estimates")
public class Estimate {

	@Id
	@GeneratedValue
	private UUID id;

	/**
	 * Written and never read back through this class: every query that reads an estimate
	 * is already scoped by it, which is what the column is for.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_item_id", nullable = false)
	private WorkItem workItem;

	/**
	 * The person, not their membership. Somebody who leaves the organisation still made
	 * this estimate, and M8 reads everything one person ever estimated.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "estimator_user_id", nullable = false)
	private User estimator;

	@Column(name = "p10_hours", nullable = false, precision = 12, scale = 2)
	private BigDecimal p10Hours;

	@Column(name = "p50_hours", nullable = false, precision = 12, scale = 2)
	private BigDecimal p50Hours;

	@Column(name = "p90_hours", nullable = false, precision = 12, scale = 2)
	private BigDecimal p90Hours;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Estimate() {
		// for JPA
	}

	public Estimate(WorkItem workItem, User estimator, BigDecimal p10Hours, BigDecimal p50Hours, BigDecimal p90Hours,
			Instant createdAt) {
		// Taken from the item rather than from a caller, so an estimate cannot be filed
		// under one organisation and against another's work.
		this.tenant = workItem.getTenant();
		this.workItem = workItem;
		this.estimator = estimator;
		this.p10Hours = p10Hours;
		this.p50Hours = p50Hours;
		this.p90Hours = p90Hours;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public WorkItem getWorkItem() {
		return workItem;
	}

	public User getEstimator() {
		return estimator;
	}

	public BigDecimal getP10Hours() {
		return p10Hours;
	}

	public BigDecimal getP50Hours() {
		return p50Hours;
	}

	public BigDecimal getP90Hours() {
		return p90Hours;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
