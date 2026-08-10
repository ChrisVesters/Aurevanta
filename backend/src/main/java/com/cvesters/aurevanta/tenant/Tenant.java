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
