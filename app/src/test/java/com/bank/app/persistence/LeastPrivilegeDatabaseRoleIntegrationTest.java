package com.bank.app.persistence;

import com.bank.app.common.TestDatabaseContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * F1/F2 regression: the least-privilege role script must leave the runtime role
 * unable to bypass the V62/V65/V67/V68 evidence guards while still allowing the
 * normal insert + retention + verification paths.
 *
 * <p>This test provisions the roles in the shared Testcontainers database once
 * per JVM. Every row-writing assertion runs inside a transaction that is rolled
 * back, so the committed audit chain used by the other integration tests is not
 * disturbed.
 */
@DisplayName("Least-privilege database roles")
class LeastPrivilegeDatabaseRoleIntegrationTest {

    private static final String APP_ROLE = "bank_app";
    private static final String APP_PASSWORD = "least-privilege-test-password";
    private static final String SCHEDULER_ROLE = "bank_scheduler";
    private static final String SCHEDULER_PASSWORD = "least-privilege-scheduler-test-password";

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void provisionRoles() throws Exception {
        postgres = TestDatabaseContainer.getInstance();
        executeScript(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), readScript());
        executeAsOwner("ALTER ROLE " + APP_ROLE + " PASSWORD '" + APP_PASSWORD + "'");
        executeAsOwner("ALTER ROLE " + SCHEDULER_ROLE + " PASSWORD '" + SCHEDULER_PASSWORD + "'");
    }

    @Test
    @DisplayName("runtime role can insert audit rows but cannot mutate or truncate them")
    void runtimeRoleIsAppendOnlyOnAudit() throws Exception {
        try (Connection owner = ownerConnection()) {
            assertThat(hasTablePrivilege(owner, APP_ROLE, "audit_logs", "SELECT")).isTrue();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "audit_logs", "INSERT")).isTrue();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "audit_logs", "UPDATE")).isFalse();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "audit_logs", "DELETE")).isFalse();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "audit_logs", "TRUNCATE")).isFalse();
        }

        assertSqlState("UPDATE audit_logs SET details = 'tampered'", "42501");
        assertSqlState("DELETE FROM audit_logs", "42501");
        assertSqlState("TRUNCATE audit_logs", "42501");
    }

    @Test
    @DisplayName("runtime role insert receives a valid chain link")
    void runtimeRoleInsertGetsChainLink() throws Exception {
        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            try (Statement statement = app.createStatement();
                 ResultSet rows = statement.executeQuery("""
                         INSERT INTO audit_logs (username, action, details, "timestamp")
                         VALUES ('role-test', CAST('ACCOUNT_CREATED' AS "AuditAction"),
                                 'least privilege', now())
                         RETURNING chain_seq, prev_hash, row_hash
                         """)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("chain_seq")).isPositive();
                assertThat(rows.getString("prev_hash")).isNotNull();
                assertThat(rows.getString("row_hash")).hasSize(64);
            } finally {
                app.rollback();
            }
        }
    }

    @Test
    @DisplayName("runtime role cannot rewrite the chain head or disable the append-only trigger")
    void runtimeRoleCannotBypassTheChain() throws Exception {
        assertSqlState("UPDATE audit_chain_state SET last_seq = 0", "42501");
        assertSqlState("ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_append_only", "42501");
    }

    @Test
    @DisplayName("runtime role can run the chain verifier")
    void runtimeRoleCanRunVerifier() throws Exception {
        try (Connection app = appConnection();
             Statement statement = app.createStatement();
             ResultSet rows = statement.executeQuery("SELECT audit_logs_first_broken_chain_seq()")) {
            assertThat(rows.next()).isTrue();
            rows.getObject(1);
        }
    }

    @Test
    @DisplayName("retention rejects a recent cutoff but is executable by the runtime role")
    void retentionIsGuardedButCallable() throws Exception {
        SQLException recent = assertThrows(SQLException.class, () -> executeAsApp(
                "SELECT delete_audit_logs_before((now() - interval '1 day')::timestamp, 1000)"));
        assertThat(recent.getSQLState()).isEqualTo("P0001");
        assertThat(recent.getMessage()).contains("at least 30 days");

        try (Connection app = appConnection()) {
            app.setAutoCommit(false);
            try (Statement statement = app.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT delete_audit_logs_before((now() - interval '400 days')::timestamp, 1000)")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isGreaterThanOrEqualTo(0);
            } finally {
                app.rollback();
            }
        }
    }

    @Test
    @DisplayName("runtime role is append-only on ledger entries")
    void runtimeRoleIsAppendOnlyOnLedger() throws Exception {
        try (Connection owner = ownerConnection()) {
            assertThat(hasTablePrivilege(owner, APP_ROLE, "ledger_entries", "SELECT")).isTrue();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "ledger_entries", "INSERT")).isTrue();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "ledger_entries", "UPDATE")).isFalse();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "ledger_entries", "DELETE")).isFalse();
            assertThat(hasTablePrivilege(owner, APP_ROLE, "ledger_entries", "TRUNCATE")).isFalse();
        }
    }

    @Test
    @DisplayName("scheduler role can read ledger entries and take its lock but cannot write or read audit")
    void schedulerRoleIsNarrow() throws Exception {
        try (Connection owner = ownerConnection()) {
            assertThat(hasTablePrivilege(owner, SCHEDULER_ROLE, "ledger_entries", "SELECT")).isTrue();
            assertThat(hasTablePrivilege(owner, SCHEDULER_ROLE, "ledger_entries", "INSERT")).isFalse();
            assertThat(hasTablePrivilege(owner, SCHEDULER_ROLE, "ledger_entries", "UPDATE")).isFalse();
            assertThat(hasTablePrivilege(owner, SCHEDULER_ROLE, "ledger_entries", "DELETE")).isFalse();
            assertThat(hasTablePrivilege(owner, SCHEDULER_ROLE, "ledger_entries", "TRUNCATE")).isFalse();
            assertThat(hasTablePrivilege(owner, SCHEDULER_ROLE, "audit_logs", "SELECT")).isFalse();
        }

        try (Connection scheduler = schedulerConnection()) {
            scheduler.setAutoCommit(false);
            try (Statement statement = scheduler.createStatement()) {
                try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM ledger_entries")) {
                    assertThat(rows.next()).isTrue();
                }
                try (ResultSet rows = statement.executeQuery(
                        "SELECT pg_try_advisory_xact_lock(hashtext('ledger-reconciliation'))")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getBoolean(1)).isTrue();
                }
            } finally {
                scheduler.rollback();
            }
        }

        SQLException auditDenied = assertThrows(SQLException.class,
                () -> executeAsScheduler("SELECT count(*) FROM audit_logs"));
        assertThat(auditDenied.getSQLState()).isEqualTo("42501");

        SQLException writeDenied = assertThrows(SQLException.class,
                () -> executeAsScheduler("DELETE FROM ledger_entries"));
        assertThat(writeDenied.getSQLState()).isEqualTo("42501");
    }

    @Test
    @DisplayName("runtime role has no schema DDL rights")
    void runtimeRoleHasNoDdl() throws Exception {
        assertSqlState("CREATE TABLE least_privilege_should_fail (id INT)", "42501");
    }

    @Test
    @DisplayName("audit reader and writer capability roles stay read/write-only")
    void auditCapabilityRolesStayNarrow() throws Exception {
        try (Connection owner = ownerConnection()) {
            assertThat(hasTablePrivilege(owner, "bank_audit_writer", "audit_logs", "INSERT")).isTrue();
            assertThat(hasTablePrivilege(owner, "bank_audit_writer", "audit_logs", "UPDATE")).isFalse();
            assertThat(hasTablePrivilege(owner, "bank_audit_writer", "audit_logs", "DELETE")).isFalse();

            assertThat(hasTablePrivilege(owner, "bank_audit_reader", "audit_logs", "SELECT")).isTrue();
            assertThat(hasTablePrivilege(owner, "bank_audit_reader", "audit_logs", "DELETE")).isFalse();

            assertThat(hasTablePrivilege(owner, "bank_ledger_maintenance", "ledger_entries", "DELETE")).isTrue();
            assertThat(hasTablePrivilege(owner, "bank_ledger_maintenance", "ledger_entries", "UPDATE")).isFalse();
        }
    }

    @Test
    @DisplayName("audit reader can select but cannot delete")
    void auditReaderIsReadOnly() throws Exception {
        try (Connection owner = ownerConnection();
             Statement statement = owner.createStatement()) {
            statement.execute("SET ROLE bank_audit_reader");
            try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM audit_logs")) {
                assertThat(rows.next()).isTrue();
            }
            SQLException denied = assertThrows(SQLException.class,
                    () -> statement.execute("DELETE FROM audit_logs"));
            assertThat(denied.getSQLState()).isEqualTo("42501");
            statement.execute("RESET ROLE");
        }
    }

    @Test
    @DisplayName("audit maintenance role can call retention inside a transaction")
    void auditMaintenanceCanCallRetention() throws Exception {
        try (Connection owner = ownerConnection()) {
            owner.setAutoCommit(false);
            try (Statement statement = owner.createStatement()) {
                statement.execute("SET ROLE bank_audit_maintenance");
                try (ResultSet rows = statement.executeQuery(
                        "SELECT delete_audit_logs_before((now() - interval '400 days')::timestamp, 1000)")) {
                    assertThat(rows.next()).isTrue();
                }
                owner.rollback();
                statement.execute("RESET ROLE");
            } finally {
                owner.setAutoCommit(true);
            }
        }
    }

    private static void executeAsOwner(String sql) throws SQLException {
        execute(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), sql);
    }

    private static void executeAsApp(String sql) throws SQLException {
        execute(postgres.getJdbcUrl(), APP_ROLE, APP_PASSWORD, sql);
    }

    private static void executeAsScheduler(String sql) throws SQLException {
        execute(postgres.getJdbcUrl(), SCHEDULER_ROLE, SCHEDULER_PASSWORD, sql);
    }

    private static void execute(String url, String user, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void executeScript(String url, String user, String password, String sql) throws SQLException {
        // The provisioning script contains DO $$ blocks and multiple
        // statements; the simple query protocol lets PostgreSQL parse the whole
        // file in one round trip.
        String simpleUrl = url + (url.contains("?") ? "&" : "?") + "preferQueryMode=simple";
        execute(simpleUrl, user, password, sql);
    }

    private static String readScript() throws Exception {
        Path direct = Path.of("scripts", "provision_least_privilege_roles.sql");
        Path parent = Path.of("..", "scripts", "provision_least_privilege_roles.sql");
        Path path = Files.exists(direct) ? direct : parent;
        assertThat(Files.exists(path))
                .as("provisioning script exists at %s", path.toAbsolutePath())
                .isTrue();
        return Files.readString(path);
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection appConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), APP_ROLE, APP_PASSWORD);
    }

    private static Connection schedulerConnection() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), SCHEDULER_ROLE, SCHEDULER_PASSWORD);
    }

    private static boolean hasTablePrivilege(Connection owner, String role, String table, String privilege)
            throws SQLException {
        try (var statement = owner.prepareStatement("SELECT has_table_privilege(?, ?, ?)")) {
            statement.setString(1, role);
            statement.setString(2, table);
            statement.setString(3, privilege);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getBoolean(1);
            }
        }
    }

    private static void assertSqlState(String sql, String expectedState) {
        SQLException error = assertThrows(SQLException.class, () -> executeAsApp(sql));
        assertThat(error.getSQLState())
                .as("SQLState for: %s", sql)
                .isEqualTo(expectedState);
    }
}
