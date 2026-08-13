package com.cvesters.aurevanta.item;

import java.time.Instant;
import java.util.UUID;

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
 * One piece of work inside a plan, and the unit that carries an estimate.
 *
 * <p>
 * A task rather than a story or an epic: coarser units hide scope growth inside the
 * estimate, and M3 models that growth separately, so it would be counted twice.
 *
 * <p>
 * Holds a {@link Tenant} of its own as well as a {@link Project}, which is a join's worth
 * of denormalisation taken on purpose. Isolation is enforced in application code, and the
 * rule is only as good as it is easy to follow — every query here can be constrained by
 * the caller's own organisation without reaching through anything.
 */
@Entity
@Table(name = "work_items")
public class WorkItem {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "archived_at")
	private Instant archivedAt;

	protected WorkItem() {
		// for JPA
	}

	public WorkItem(Project project, String title, String description, Instant createdAt) {
		// Taken from the project rather than from a caller, so an item cannot be filed
		// under one organisation and against another's plan.
		this.tenant = project.getTenant();
		this.project = project;
		this.title = title;
		this.description = description;
		this.createdAt = createdAt;
	}

	/**
	 * Changes what the item is called and what is said about it. Both together, because
	 * both are sent together; a null description clears it.
	 */
	public void describe(String title, String description) {
		this.title = title;
		this.description = description;
	}

	/**
	 * Puts the item away without destroying it, and keeps the first moment it was — the
	 * same rule projects follow, for the same reason.
	 *
	 * <p>
	 * Nothing in M2 deletes one. From step 3 an item carries estimates, which are what M8
	 * measures a person's calibration against; an item that could be deleted would be a
	 * way to lose that evidence long before anything reads it.
	 */
	public void archive(Instant at) {
		if (this.archivedAt == null) {
			this.archivedAt = at;
		}
	}

	/** Brings it back into the default listing. */
	public void unarchive() {
		this.archivedAt = null;
	}

	public UUID getId() {
		return id;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public Project getProject() {
		return project;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

}
