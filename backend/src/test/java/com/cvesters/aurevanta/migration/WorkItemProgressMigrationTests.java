package com.cvesters.aurevanta.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * <strong>V16 deliberately backfills nothing, and this is what says so out loud.</strong>
 *
 * <p>
 * That is an odd thing to test until you notice the alternative reading: "we wrote
 * nothing on purpose" and "the backfill silently missed every row" leave the table in
 * exactly the same state. {@code EstimateElicitationMigrationTests} exists because
 * {@code V13} made a backfill with no test at all; this exists for the mirror-image
 * reason, because the decision here was not to make one.
 *
 * <p>
 * The decision itself follows {@code V14} rather than {@code V13}. A work item already
 * holding a start date has no reporter and no report instant, so three of this table's
 * six claim columns would have to be invented — in the one table whose entire purpose is
 * to say who said what and when. A fabricated row is indistinguishable from a real one to
 * every reader downstream, and corrupted evidence still looks like data.
 *
 * <p>
 * Run the way the other two in this package run: bring the database to the version
 * before, fill it with rows of the shape that version produced, and only then migrate.
 */
@Testcontainers
class WorkItemProgressMigrationTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-18T08:00:00Z");

	private static final LocalDate STARTED = LocalDate.parse("2026-08-10");

	private static final UUID ACME = UUID.randomUUID();

	private static final UUID ADA = UUID.randomUUID();

	private static final UUID PLAN = UUID.randomUUID();

	private static final UUID STARTED_ITEM = UUID.randomUUID();

	private static final UUID UNTOUCHED_ITEM = UUID.randomUUID();

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

	@BeforeAll
	static void migrateAcrossTheTable() throws SQLException {
		migrateTo("15");
		try (Connection connection = connect()) {
			insert(connection, "insert into tenants (id, name, slug, created_at) values (?, ?, ?, ?)", ACME, "Acme",
					"acme", Timestamp.from(CREATED_AT));
			insert(connection,
					"insert into users (id, email, password_hash, display_name, created_at) values (?, ?, ?, ?, ?)",
					ADA, "ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", Timestamp.from(CREATED_AT));
			insert(connection, "insert into projects (id, tenant_id, name, created_at) values (?, ?, ?, ?)", PLAN, ACME,
					"Q3 platform work", Timestamp.from(CREATED_AT));
			// The shape V15 produced: progress written straight onto the item, with
			// nothing anywhere saying who reported it or when.
			insert(connection,
					"insert into work_items (id, tenant_id, project_id, title, status, started_on, created_at)"
							+ " values (?, ?, ?, ?, ?, ?, ?)",
					STARTED_ITEM, ACME, PLAN, "Migrate the auth service", "IN_PROGRESS", STARTED,
					Timestamp.from(CREATED_AT));
			insert(connection,
					"insert into work_items (id, tenant_id, project_id, title, status, created_at)"
							+ " values (?, ?, ?, ?, ?, ?)",
					UNTOUCHED_ITEM, ACME, PLAN, "Roll it out", "NOT_STARTED", Timestamp.from(CREATED_AT));
		}
		migrateTo("16");
	}

	/**
	 * The whole of the decision. An item that was already under way gets no report,
	 * because nobody knows who said it was — and the boundary calibration measures
	 * against falls back to the column on the item for exactly these rows.
	 */
	@Test
	void nothingIsInventedOnBehalfOfSomebodyWhoNeverMadeAReport() throws SQLException {
		assertThat(rowCount("work_item_progress")).isZero();
	}

	/**
	 * The claim it was written over is still on the item, which is what makes that safe.
	 */
	@Test
	void theProgressAlreadyRecordedIsUntouched() throws SQLException {
		assertThat(startedOn(STARTED_ITEM)).isEqualTo(STARTED);
		assertThat(startedOn(UNTOUCHED_ITEM)).isNull();
	}

	/**
	 * A report is a thing a person said, so it cannot be filed under nobody — the column
	 * has no default and never will, for the reason {@code elicitation_method} dropped
	 * its own.
	 */
	@Test
	void aReportCannotBeFiledWithoutSayingWhoMadeIt() {
		assertThatExceptionOfType(SQLException.class).isThrownBy(() -> {
			try (Connection connection = connect()) {
				insert(connection,
						"insert into work_item_progress (id, tenant_id, work_item_id, reported_at, status)"
								+ " values (?, ?, ?, ?, ?)",
						UUID.randomUUID(), ACME, STARTED_ITEM, Timestamp.from(CREATED_AT), "IN_PROGRESS");
			}
		});
	}

	/**
	 * Two identical claims are two people saying so, or one person saying so twice. A
	 * unique constraint here would be this table deciding which claims were worth
	 * keeping, which is the behaviour it exists to replace — so its absence is asserted
	 * rather than left to be noticed.
	 */
	@Test
	void theSameClaimMayBeMadeTwice() throws SQLException {
		// Rolled back rather than cleaned up, so this case cannot leave a row behind for
		// the one above to count — the two assertions are about the same empty table.
		try (Connection connection = connect()) {
			connection.setAutoCommit(false);
			report(connection);
			report(connection);
			try (PreparedStatement statement = connection.prepareStatement("select count(*) from work_item_progress");
					ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).isTrue();
				assertThat(rows.getInt(1)).isEqualTo(2);
			}
			connection.rollback();
		}
		assertThat(rowCount("work_item_progress")).isZero();
	}

	private static void report(Connection connection) throws SQLException {
		insert(connection,
				"insert into work_item_progress (id, tenant_id, work_item_id, reported_by_user_id, reported_at,"
						+ " status, started_on) values (?, ?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), ACME, STARTED_ITEM, ADA, Timestamp.from(CREATED_AT), "IN_PROGRESS", STARTED);
	}

	private static int rowCount(String table) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement("select count(*) from " + table);
				ResultSet rows = statement.executeQuery()) {
			assertThat(rows.next()).isTrue();
			return rows.getInt(1);
		}
	}

	private static LocalDate startedOn(UUID itemId) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection
					.prepareStatement("select started_on from work_items where id = ?")) {
			statement.setObject(1, itemId);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).as("an item %s", itemId).isTrue();
				return rows.getObject(1, LocalDate.class);
			}
		}
	}

	private static void insert(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int at = 0; at < values.length; at++) {
				statement.setObject(at + 1, values[at]);
			}
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
