package com.cvesters.aurevanta.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
 * <strong>V15 backfills a claim about every estimate ever written, and this is what says
 * the claim is true.</strong> That column is the only instrument that can ever answer
 * M5's own question — whether changing how a range is asked for changes how often it
 * contains the truth — so a backfill that quietly missed rows, or a default left in place
 * letting later rows inherit a method nobody stated, would corrupt the evidence rather
 * than lose it. The distinction matters: corrupted evidence still looks like data.
 *
 * <p>
 * Run the way {@code IdentityAndMembershipMigrationTests} runs: bring the database to the
 * version before, fill it with rows of the shape that version produced, and only then
 * migrate. V13 made the same kind of backfill with no test at all, which is why this one
 * is written.
 */
@Testcontainers
class EstimateElicitationMigrationTests {

	private static final Instant CREATED_AT = Instant.parse("2026-08-17T08:00:00Z");

	private static final UUID ACME = UUID.randomUUID();

	private static final UUID ADA = UUID.randomUUID();

	private static final UUID PLAN = UUID.randomUUID();

	private static final UUID ITEM = UUID.randomUUID();

	private static final UUID ESTIMATE = UUID.randomUUID();

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

	@BeforeAll
	static void migrateAcrossTheBackfill() throws SQLException {
		migrateTo("14");
		try (Connection connection = connect()) {
			insert(connection, "insert into tenants (id, name, slug, created_at) values (?, ?, ?, ?)", ACME, "Acme",
					"acme", Timestamp.from(CREATED_AT));
			insert(connection,
					"insert into users (id, email, password_hash, display_name, created_at) values (?, ?, ?, ?, ?)",
					ADA, "ada@acme.test", "{bcrypt}$2a$10$hash", "Ada", Timestamp.from(CREATED_AT));
			insert(connection, "insert into projects (id, tenant_id, name, created_at) values (?, ?, ?, ?)", PLAN, ACME,
					"Q3 platform work", Timestamp.from(CREATED_AT));
			insert(connection,
					"insert into work_items (id, tenant_id, project_id, title, status, created_at)"
							+ " values (?, ?, ?, ?, ?, ?)",
					ITEM, ACME, PLAN, "Migrate the auth service", "NOT_STARTED", Timestamp.from(CREATED_AT));
			// The shape V14 produced: three numbers, an estimator, and no word anywhere
			// about how they were asked for, because there was only one way to ask.
			insert(connection,
					"insert into estimates (id, tenant_id, work_item_id, estimator_user_id, p10_hours, p50_hours,"
							+ " p90_hours, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
					ESTIMATE, ACME, ITEM, ADA, 3, 5, 12, Timestamp.from(CREATED_AT));
		}
		migrateTo("15");
	}

	/**
	 * The backfill, and the reason it is honest rather than merely convenient: every row
	 * that existed before M5 really was typed into three boxes, because that is the only
	 * form this product has ever had. Compare {@code V14}, which wrote nothing because
	 * there was nothing true to write.
	 */
	@Test
	void everyEstimateWrittenBeforeM5SaysItWasThreeBoxes() throws SQLException {
		assertThat(methodOf(ESTIMATE)).isEqualTo("three_point");
	}

	/**
	 * <strong>And then the default goes, which is the point of doing it in two
	 * statements.</strong> A default left in place would let an insert omit the method
	 * and be handed one by the database — a row claiming to have been collected a way
	 * nobody said it was, in the column whose whole purpose is to say how it was.
	 */
	@Test
	void nothingWrittenAfterwardsMayLeaveTheQuestionUnanswered() {
		assertThatExceptionOfType(SQLException.class).isThrownBy(() -> {
			try (Connection connection = connect()) {
				insert(connection,
						"insert into estimates (id, tenant_id, work_item_id, estimator_user_id, p10_hours, p50_hours,"
								+ " p90_hours, created_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
						UUID.randomUUID(), ACME, ITEM, ADA, 8, 13, 30, Timestamp.from(CREATED_AT));
			}
		});
	}

	@Test
	void theColumnCarriesNoDefaultToInheritFrom() throws SQLException {
		assertThat(defaultOf("estimates", "elicitation_method")).isNull();
	}

	private static String methodOf(UUID estimateId) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection
					.prepareStatement("select elicitation_method from estimates where id = ?")) {
			statement.setObject(1, estimateId);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).as("an estimate %s", estimateId).isTrue();
				return rows.getString(1);
			}
		}
	}

	private static String defaultOf(String table, String column) throws SQLException {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"select column_default from information_schema.columns where table_name = ? and column_name = ?")) {
			statement.setString(1, table);
			statement.setString(2, column);
			try (ResultSet rows = statement.executeQuery()) {
				assertThat(rows.next()).as("a column %s.%s", table, column).isTrue();
				return rows.getString(1);
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
