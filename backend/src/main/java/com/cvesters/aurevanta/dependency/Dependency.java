package com.cvesters.aurevanta.dependency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.project.Project;
import com.cvesters.aurevanta.tenant.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One piece of work has to finish before another can begin.
 *
 * <p>
 * Finish-to-start, with a lag — the only kind of edge this product models. It covers the
 * overwhelming majority of real plans, and the other three types multiply the scheduler's
 * complexity for cases most teams never draw.
 *
 * <p>
 * An edge is the difference between a queue and a plan: the same items forecast at wildly
 * different dates depending only on how they are joined up, so this row is not decoration
 * on the schema — it is half of what the simulation engine reads.
 */
@Entity
@Table(name = "dependencies")
public class Dependency {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "predecessor_item_id", nullable = false)
	private WorkItem predecessor;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "successor_item_id", nullable = false)
	private WorkItem successor;

	@Column(name = "lag_hours", nullable = false, precision = 12, scale = 2)
	private BigDecimal lagHours;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Dependency() {
		// for JPA
	}

	public Dependency(WorkItem predecessor, WorkItem successor, BigDecimal lagHours, Instant createdAt) {
		// Both taken from the predecessor, which the service has already established is
		// in
		// the same plan as the successor — so an edge cannot be filed under an
		// organisation or a project that neither of its ends belongs to.
		this.tenant = predecessor.getTenant();
		this.project = predecessor.getProject();
		this.predecessor = predecessor;
		this.successor = successor;
		this.lagHours = lagHours;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public WorkItem getPredecessor() {
		return predecessor;
	}

	public WorkItem getSuccessor() {
		return successor;
	}

	public BigDecimal getLagHours() {
		return lagHours;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
