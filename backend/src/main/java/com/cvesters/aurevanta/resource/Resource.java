package com.cvesters.aurevanta.resource;

import java.time.Instant;
import java.util.UUID;

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
 * A pool of a thing a plan needs, with a name and a number of units.
 *
 * <p>
 * <strong>One concept where {@code roadmap.md} describes two.</strong> That section has
 * resources, then types over them, then requirements against either — and the measurement
 * in {@code docs/design/resources-and-people.md} needed none of the hierarchy: what moved
 * a forecast by 14% to 59% was work being unable to cross from one pool to another, and
 * nothing in it depended on which individual did what. So every case it lists is one of
 * these. <em>Backend engineers × 3</em> is the type-level requirement said directly,
 * <em>Staging environment × 1</em> is the licence and equipment case said identically,
 * and <em>Ada × 1</em> is a person, which is a pool of one and needs no second concept.
 *
 * <p>
 * <strong>The person is a convenience and never a permission.</strong> Linking a pool to
 * a user makes it findable; it grants nothing, and nothing in this product reports on how
 * busy anybody is. The moment a screen ranks people by utilisation this has become a tool
 * aimed at individuals rather than at plans, which is calibration's <em>people are named
 * and never ranked</em> arriving in a second place.
 *
 * <p>
 * The name is not unique and nothing is derived from it, for {@code Project}'s reason and
 * the handle change's.
 */
@Entity
@Table(name = "resources")
public class Resource {

	@Id
	@GeneratedValue
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id", nullable = false)
	private Tenant tenant;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false)
	private int units;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User person;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "archived_at")
	private Instant archivedAt;

	protected Resource() {
		// for JPA
	}

	public Resource(Tenant tenant, String name, int units, User person, Instant createdAt) {
		this.tenant = tenant;
		this.name = name;
		this.units = units;
		this.person = person;
		this.createdAt = createdAt;
	}

	/**
	 * Changes what the pool is called, how many of it there are, and who it is.
	 *
	 * <p>
	 * All three at once, because all three are sent at once: the request carries the
	 * whole of what may be changed, so there is no absent field to interpret. A null
	 * person clears the link, which is a state the column allows.
	 *
	 * <p>
	 * <strong>Changing the units does not move a forecast already made.</strong> Every
	 * run copies the declaration it was scheduled under, the way it copies the estimates
	 * — so this is a claim about the team from now on rather than a rewrite of what was
	 * assumed last month.
	 */
	public void describe(String name, int units, User person) {
		this.name = name;
		this.units = units;
		this.person = person;
	}

	/**
	 * Puts the pool away without destroying it.
	 *
	 * <p>
	 * Keeps the first moment, the way {@code Project.archive} does: archiving something
	 * already archived is a no-op arriving twice rather than a fresh decision.
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

	public String getName() {
		return name;
	}

	public int getUnits() {
		return units;
	}

	/** Null unless this pool is a particular person, which most are not. */
	public User getPerson() {
		return person;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getArchivedAt() {
		return archivedAt;
	}

}
