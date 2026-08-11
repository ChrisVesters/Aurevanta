package com.cvesters.aurevanta.problem;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.cvesters.aurevanta.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ties the constraint names {@code ApiExceptionHandler} reads to the ones the database
 * actually has.
 *
 * <p>
 * Those names belong to migrations, not to the code that reads them, and nothing else
 * would notice the two drifting apart: renaming an index would quietly turn a specific,
 * actionable refusal into a generic one, with every test still green. This is the test
 * that is supposed to fail instead.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConstraintNamesTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void everyConstraintTheAdviceReadsIsStillCalledThat() {
		assertThat(ApiExceptionHandler.KNOWN_CONSTRAINTS).isNotEmpty().allSatisfy((name) -> {
			Integer found = this.jdbc.queryForObject("select count(*) from pg_indexes where indexname = ?",
					Integer.class, name);
			assertThat(found).as("an index named %s", name).isEqualTo(1);
		});
	}

}
