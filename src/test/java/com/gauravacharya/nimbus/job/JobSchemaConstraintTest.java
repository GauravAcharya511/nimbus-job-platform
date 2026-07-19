package com.gauravacharya.nimbus.job;

import com.gauravacharya.nimbus.support.PostgresTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies database-level integrity rules independent of application code.
 * These would silently pass against an in-memory database like H2.
 */
@SpringBootTest
@Import(PostgresTestContainer.class)
class JobSchemaConstraintTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("database rejects a status outside the allowed set")
    void rejectsInvalidStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO jobs (type, status) VALUES (?, ?)", "x", "NOT_A_STATUS"))
                .hasMessageContaining("chk_jobs_status");
    }

    @Test
    @DisplayName("database rejects attempts greater than max_attempts")
    void rejectsAttemptsOverMax() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO jobs (type, status, attempts, max_attempts) VALUES (?,?,?,?)",
                "x", "PENDING", 5, 3))
                .hasMessageContaining("chk_jobs_attempts");
    }
}
