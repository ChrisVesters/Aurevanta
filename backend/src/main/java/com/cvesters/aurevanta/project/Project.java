package com.cvesters.aurevanta.project;

import java.time.Instant;
import java.util.UUID;

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
 * A named container for one plan, belonging to exactly one organisation.
 *
 * <p>
 * The name is not unique and nothing is derived from it. Two projects in one organisation
 * may share one — a team running the same shape of work every quarter is the ordinary
 * case — and {@link #getId()} is what addresses one. That is the lesson M1a paid a
 * milestone for, applied before the mistake rather than after it.
 */
@Entity
@Table(name = "projects")
public class Project {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 2000)
	private String description;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "archived_at")
	private Instant archivedAt;

	protected Project() {
		// for JPA
	}

	public Project(Tenant tenant, String name, String description, Instant createdAt) {
		this.tenant = tenant;
		this.name = name;
		this.description = description;
		this.createdAt = createdAt;
	}

	/**
	 * Changes what the project is called and what is said about it.
	 *
	 * <p>
	 * Both at once, because both are sent at once: the request carries the whole of what
	 * may be changed, so there is no absent field to interpret. A null description clears
	 * it, which is a state the column allows and a name is not.
	 */
	public void describe(String name, String description) {
		this.name = name;
		this.description = description;
	}

	/**
	 * Puts the project away without destroying it.
	 *
	 * <p>
	 * The only way anything leaves a listing in M2. Every member may write plan data, so
	 * a delete would be one person destroying a colleague's work with nothing to put back
	 * — and the estimates hanging off a project are the evidence M8 calibrates against
	 * long after anybody would have thought to keep them.
	 *
	 * <p>
	 * Keeps the first moment, the way {@code User.markEmailVerified} does: archiving
	 * something already archived is a no-op arriving twice, not a fresh decision, and
	 * rewriting the timestamp would move it up an ordering that reads as "most recently
	 * put away".
	 */
	public void archive(Instant at) {
		if (this.archivedAt == null) {
			this.archivedAt = at;
		}
	}

	/**
	 * Brings it back into the default listing. Any member may, as any member may archive.
	 */
	public void unarchive() {
		this.archivedAt = null;
	}

	public UUID getId() {
		return id;
	}

	public Tenant getTenant() {
		return tenant;
	}

	public String getName() {
		return name;
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
