package com.cvesters.aurevanta.tenant;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An organisation, and the unit of data isolation. Everything a user creates belongs to
 * exactly one tenant and is never visible outside it.
 */
@Entity
@Table(name = "tenants")
public class Tenant {

	@Id
	@GeneratedValue
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	/** Stable, URL-safe handle derived from the name; unique across the installation. */
	@Column(nullable = false, length = 80)
	private String slug;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Tenant() {
		// for JPA
	}

	public Tenant(String name, String slug, Instant createdAt) {
		this.name = name;
		this.slug = slug;
		this.createdAt = createdAt;
	}

	/**
	 * Changes what the organisation is called. Nothing is derived from the name, so this
	 * is the whole of it — the handle is its own field and stays where it is.
	 */
	public void rename(String name) {
		this.name = name;
	}

	/**
	 * Changes the address the organisation answers to.
	 *
	 * <p>
	 * <strong>Every link anyone holds to the old handle stops working.</strong> There is
	 * no table of retired handles and nothing redirects; the decision was taken in M1a on
	 * the grounds that nothing routes by handle yet, which is true until M2 puts one in a
	 * URL and stops being true the moment it does. The screen that calls this says so
	 * before it saves.
	 */
	public void changeSlug(String slug) {
		this.slug = slug;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
