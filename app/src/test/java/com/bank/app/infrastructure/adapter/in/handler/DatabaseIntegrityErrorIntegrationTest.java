package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Verifies the SQLSTATE classifier against PostgreSQL rather than a fabricated exception tree. */
class DatabaseIntegrityErrorIntegrationTest extends AbstractIntegrationTest {

    private final JdbcTemplate jdbc;

    @Autowired
    DatabaseIntegrityErrorIntegrationTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new ProblemMessageResolver(new StaticMessageSource()));

    @Test
    void duplicateKeyUsesUniqueProblemCode() {
        jdbc.execute("CREATE TEMP TABLE audit_unique_check (id BIGINT PRIMARY KEY) ON COMMIT DROP");
        jdbc.update("INSERT INTO audit_unique_check (id) VALUES (1)");

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO audit_unique_check (id) VALUES (1)"));

        assertProblemCode(failure, "UNIQUE_CONSTRAINT_VIOLATION");
    }

    @Test
    void foreignKeyUsesGenericIntegrityProblemCode() {
        jdbc.execute("CREATE TEMP TABLE audit_fk_parent (id BIGINT PRIMARY KEY) ON COMMIT DROP");
        jdbc.execute("CREATE TEMP TABLE audit_fk_child (parent_id BIGINT REFERENCES audit_fk_parent(id)) ON COMMIT DROP");

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO audit_fk_child (parent_id) VALUES (999)"));

        assertProblemCode(failure, "DB_INTEGRITY_VIOLATION");
    }

    @Test
    void checkConstraintUsesGenericIntegrityProblemCode() {
        jdbc.execute("CREATE TEMP TABLE audit_check_value (amount INT CHECK (amount > 0)) ON COMMIT DROP");

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO audit_check_value (amount) VALUES (0)"));

        assertProblemCode(failure, "DB_INTEGRITY_VIOLATION");
    }

    private void assertProblemCode(DataIntegrityViolationException failure, String expectedCode) {
        var response = handler.handleDataIntegrityViolationException(failure, null);
        assertNotNull(response.getBody());
        assertEquals(expectedCode, response.getBody().getProperties().get("code"));
    }
}
