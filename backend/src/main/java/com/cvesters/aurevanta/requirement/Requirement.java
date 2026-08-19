package com.cvesters.aurevanta.requirement;

import java.time.Instant;
import java.util.UUID;

import com.cvesters.aurevanta.item.WorkItem;
import com.cvesters.aurevanta.resource.Resource;
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
 * What one piece of work needs before it can be under way.
 *
 * <p>
 * <strong>Held for the whole of its duration and never partly given back</strong>, which
 * follows from the non-preemption {@code Schedule} already commits to: once something
 * starts it runs to completion, so there is no moment at which half of what it holds
 * could be released. A task that needs a backend engineer and the staging environment
 * needs both, together, from the moment it starts until it ends.
 *
 * <p>
 * <strong>{@link #getUnits()} is occupancy and never speed.</strong> Two units means the
 * item ties up two, not that it goes twice as fast. {@code roadmap.md} says that with an
 * allocation "the schema's stored effort finally converts to duration honestly" and
 * {@code docs/design/resources-and-people.md} decision 5 disagrees: the estimate is what
 * somebody said the <em>task</em> would take and already implies whoever does it, effort
 * divided by headcount is linear speed-up with no communication cost, and — the reason
 * that decides it — there is no oracle. Every modelling decision in the simulation engine
 * is checkable against arithmetic that exists outside this codebase, and "two people
 * finish this in 60% of the time" is checkable against nothing.
 *
 * <p>
 * <strong>Its own package, pointing at two others and pointed at by neither.</strong>
 * {@code estimate} and {@code dependency} both hang off {@code item}; this hangs off
 * {@code item} and {@code resource} at once, so putting it in either would have made
 * those two depend on each other for the sake of a join.
 */
@Entity
@Table(name = "requirements")
public class Requirement {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "work_item_id", nullable = false)
	private WorkItem workItem;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "resource_id", nullable = false)
	private Resource resource;

	@Column(nullable = false)
	private int units;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Requirement() {
		// for JPA
	}

	public Requirement(Tenant tenant, WorkItem workItem, Resource resource, int units, Instant createdAt) {
		this.tenant = tenant;
		this.workItem = workItem;
		this.resource = resource;
		this.units = units;
		this.createdAt = createdAt;
	}

	public WorkItem getWorkItem() {
		return workItem;
	}

	public Resource getResource() {
		return resource;
	}

	public int getUnits() {
		return units;
	}

}
