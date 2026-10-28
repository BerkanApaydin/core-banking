package com.bank.app.persistence;

import com.bank.app.BankApplication;
import com.bank.app.audit.application.port.out.AuditChainVerificationPort;
import com.bank.app.common.AbstractSpringBootIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F1 regression: V67 links every audit row to its predecessor and adds a
 * database verifier. These tests exercise the trigger and the verifier through
 * raw JDBC, below the adapter, so a future migration or ORM change that
 * silently drops the chain turns red.
 *
 * <p>Every test runs in a rolled-back transaction: audit rows are append-only
 * and the shared Testcontainers database must keep its committed chain intact.
 */
@SpringBootTest(classes = BankApplication.class)
@DisplayName("Audit hash chain")
class AuditChainIntegrityIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AuditChainVerificationPort verificationPort;

    @Autowired
    AuditChainIntegrityIntegrationTest(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            AuditChainVerificationPort verificationPort,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.verificationPort = verificationPort;
    }

    @Test
    @DisplayName("appended audit rows form a hash chain")
    void appendedRowsFormChain() {
        inRolledBackTransaction(() -> {
            long firstId = insertAudit("chain-a", LocalDateTime.now());
            long secondId = insertAudit("chain-b", LocalDateTime.now());

            ChainRow first = load(firstId);
            ChainRow second = load(secondId);

            assertThat(first.chainSeq()).isPositive();
            assertThat(second.chainSeq()).isEqualTo(first.chainSeq() + 1);
            assertThat(second.prevHash()).isEqualTo(first.rowHash());
            assertThat(first.rowHash()).hasSize(64);
            assertThat(verificationPort.findFirstBrokenChainSeq()).isEmpty();
        });
    }

    @Test
    @DisplayName("rewriting a retained row is detected")
    void rewrittenRowIsDetected() {
        inRolledBackTransaction(() -> {
            long id = insertAudit("original", LocalDateTime.now());
            long chainSeq = load(id).chainSeq();

            jdbcTemplate.execute("ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_append_only");
            jdbcTemplate.update("UPDATE audit_logs SET details = ? WHERE id = ?", "tampered", id);
            jdbcTemplate.execute("ALTER TABLE audit_logs ENABLE TRIGGER trg_audit_logs_append_only");

            assertThat(verificationPort.findFirstBrokenChainSeq())
                    .isEqualTo(OptionalLong.of(chainSeq));
        });
    }

    @Test
    @DisplayName("deleting a row from the middle of the chain is detected")
    void middleDeletionIsDetected() {
        inRolledBackTransaction(() -> {
            long firstId = insertAudit("middle-a", LocalDateTime.now());
            long middleId = insertAudit("middle-b", LocalDateTime.now());
            long lastId = insertAudit("middle-c", LocalDateTime.now());
            long lastSeq = load(lastId).chainSeq();
            assertThat(load(firstId).chainSeq()).isLessThan(load(middleId).chainSeq());

            enableAuditMaintenance();
            jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", middleId);

            assertThat(verificationPort.findFirstBrokenChainSeq())
                    .isEqualTo(OptionalLong.of(lastSeq));
        });
    }

    @Test
    @DisplayName("deleting the newest row is detected by the chain head")
    void newestDeletionIsDetectedByChainHead() {
        inRolledBackTransaction(() -> {
            long survivingId = insertAudit("suffix-a", LocalDateTime.now());
            long deletedId = insertAudit("suffix-b", LocalDateTime.now());
            long survivingSeq = load(survivingId).chainSeq();
            assertThat(load(deletedId).chainSeq()).isEqualTo(survivingSeq + 1);

            enableAuditMaintenance();
            jdbcTemplate.update("DELETE FROM audit_logs WHERE id = ?", deletedId);

            assertThat(verificationPort.findFirstBrokenChainSeq())
                    .isEqualTo(OptionalLong.of(survivingSeq));
        });
    }

    @Test
    @DisplayName("retention deletes only a chain prefix and remains verifiable")
    void retentionDeletesOnlyChainPrefix() {
        inRolledBackTransaction(() -> {
            enableAuditMaintenance();
            jdbcTemplate.update("DELETE FROM audit_logs");

            LocalDateTime now = LocalDateTime.now();
            long oldFirstId = insertAudit("old-first", now.minusDays(400));
            long newMiddleId = insertAudit("new-middle", now);
            long oldTailId = insertAudit("old-tail", now.minusDays(400));
            long oldFirstSeq = load(oldFirstId).chainSeq();
            assertThat(load(newMiddleId).chainSeq()).isEqualTo(oldFirstSeq + 1);
            assertThat(load(oldTailId).chainSeq()).isEqualTo(oldFirstSeq + 2);

            int deleted = jdbcTemplate.queryForObject(
                    "SELECT delete_audit_logs_before(?, ?)", Integer.class,
                    now.minusDays(365), 1000);

            assertThat(deleted)
                    .as("a backdated row behind a newer row must not punch a chain hole")
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audit_logs WHERE id = ?", Integer.class, oldFirstId))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audit_logs WHERE id = ?", Integer.class, oldTailId))
                    .as("the backdated tail waits until the newer rows also expire")
                    .isEqualTo(1);
            assertThat(verificationPort.findFirstBrokenChainSeq()).isEmpty();
        });
    }

    private long insertAudit(String details, LocalDateTime timestamp) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO audit_logs (username, action, details, "timestamp")
                VALUES (?, CAST(? AS "AuditAction"), ?, ?)
                RETURNING id
                """, Long.class, "chain-test", "ACCOUNT_CREATED", details, timestamp);
    }

    private ChainRow load(long id) {
        return jdbcTemplate.queryForObject("""
                SELECT chain_seq, prev_hash, row_hash
                  FROM audit_logs
                 WHERE id = ?
                """, (rs, rowNum) -> new ChainRow(
                        rs.getLong("chain_seq"),
                        rs.getString("prev_hash"),
                        rs.getString("row_hash")), id);
    }

    private void enableAuditMaintenance() {
        jdbcTemplate.queryForObject(
                "SELECT set_config('app.audit_maintenance', 'on', true)", String.class);
    }

    private void inRolledBackTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> {
            work.run();
            status.setRollbackOnly();
        });
    }

    private record ChainRow(long chainSeq, String prevHash, String rowHash) {
    }
}
