package com.cvesters.aurevanta.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V3 moves an existing account's organisation and role onto a membership row. Nothing is
 * deployed yet, but a migration that silently drops that data would be discovered only by
 * whoever ran it — so it is exercised against the schema it actually upgrades: the
 * database is brought to V2, filled with accounts of the shape V2 produced, and only then
 * migrated.
 */
@Testcontainers
class IdentityAndMembershipMigrationTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-06T08:00:00Z");

	private static final UUID ACME = UUID.randomUUID();

	private static final UUID UMBRELLA = UUID.randomUUID();

	private static final UUID ADA = UUID.randomUUID();

	private static final UUID GRACE = UUID.randomUUID();

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

	@BeforeAll
	static void migrateAcrossTheSplit() throws SQLException {
		migrateTo("2");
		try (Connection connection = connect()) {
			insertTenant(connection, ACME, "Acme Planning Co", "acme-planning-co");
			insertTenant(connection, UMBRELLA, "Umbrella", "umbrella");
			insertUser(connection, ADA, ACME, "ada@acme.test", "OWNER");
			insertUser(connection, GRACE, UMBRELLA, "grace@umbrella.test", "MEMBER");
		}
		migrateTo("3");
	}

	@Test
	void everyExistingUserKeepsTheirOrganisationAndRole() throws SQLException {
		assertThat(membershipOf(ADA)).isEqualTo(new Membership(ACME, "OWNER", CREATED_AT));
		assertThat(membershipOf(GRACE)).isEqualTo(new Membership(UMBRELLA, "MEMBER", CREATED_AT));
	}

	@Test
	void everyExistingUserGetsExactlyOneMembership() throws SQLException {
		assertThat(count("select count(*) from memberships")).isEqualTo(2);
	}

	@Test
	void anExistingMembershipHasNotBeenAccessedYet() throws SQLException {
		assertThat(count("select count(*) from memberships where last_accessed_at is not null")).isZero();
	}

	@Test
	void theOrganisationAndRoleAreGoneFromTheAccountItself() throws SQLException {
		assertThat(columnsOf("users")).contains("email", "password_hash", "display_name")
			.doesNotContain("tenant_id", "role");
	}

	/**
	 * The address still identifies one person across the whole installation — that is
	 * what lets a single identity span organisations, so the index has to survive the
	 * split.
	 */
	@Test
	void theAddressStaysUniqueAcrossTheInstallation() throws SQLException {
		assertThat(count("select count(*) from pg_indexes where tablename = 'users' and indexname = 'uq_users_email'"))
			.isEqualTo(1);
	}

	private record Membership(UUID tenantId, String role, Instant createdAt) {
	}

	private Membership membershipOf(UUID userId) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection
					.prepareStatement("select tenant_id, role, created_at from memberships where user_id = ?")) {
			statement.setObject(1, userId);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).as("a membership for %s", userId).isTrue();
				return new Membership(rows.getObject("tenant_id", UUID.class), rows.getString("role"),
						rows.getTimestamp("created_at").toInstant());
			}
		}
	}

	private List<String> columnsOf(String table) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection
					.prepareStatement("select column_name from information_schema.columns where table_name = ?")) {
			statement.setString(1, table);
			try (ResultSet rows = statement.executeQuery()) {
				List<String> columns = new ArrayList<>();
				while (rows.next()) {
					columns.add(rows.getString(1));
				}
				return columns;
			}
		}
	}

	private long count(String sql) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(sql);
				ResultSet rows = statement.executeQuery()) {
			rows.next();
			return rows.getLong(1);
		}
	}

	private static void insertTenant(Connection connection, UUID id, String name, String slug) throws SQLException {
		try (PreparedStatement statement = connection
			.prepareStatement("insert into tenants (id, name, slug, created_at) values (?, ?, ?, ?)")) {
			statement.setObject(1, id);
			statement.setString(2, name);
			statement.setString(3, slug);
			statement.setTimestamp(4, Timestamp.from(CREATED_AT));
			statement.executeUpdate();
		}
	}

	private static void insertUser(Connection connection, UUID id, UUID tenantId, String email, String role)
			throws SQLException {
		try (PreparedStatement statement = connection
			.prepareStatement("insert into users (id, tenant_id, email, password_hash, display_name, role, created_at) "
					+ "values (?, ?, ?, ?, ?, ?, ?)")) {
			statement.setObject(1, id);
			statement.setObject(2, tenantId);
			statement.setString(3, email);
			statement.setString(4, "{bcrypt}$2a$10$hash");
			statement.setString(5, email.substring(0, email.indexOf('@')));
			statement.setString(6, role);
			statement.setTimestamp(7, Timestamp.from(CREATED_AT));
			statement.executeUpdate();
		}
	}

	private static void migrateTo(String version) {
		Flyway.configure()
			.dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
			.locations("classpath:db/migration")
			.target(MigrationVersion.fromVersion(version))
			.load()
			.migrate();
	}

	private static Connection connect() throws SQLException {
		return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
	}

}
