package com.bank.app.persistence;

import com.bank.app.BankApplication;
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
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F8 regression: after the audit partition cutover, retention should drop a
 * fully expired partition (metadata-only) instead of deleting every row, but
 * only when the dropped rows are a true hash-chain prefix. A backdated row can
 * carry a high chain_seq inside an old timestamp partition; dropping that
 * partition would remove a high-sequence row while lower-sequence rows survive
 * and make the V67 verifier report a hole.
 *
 * <p>The test temporarily replaces audit_logs with a partitioned copy inside a
 * rolled-back transaction, so the shared Testcontainers schema is restored.
 */
@SpringBootTest(classes = BankApplication.class)
@DisplayName("Audit partition-aware retention")
class AuditPartitionRetentionIntegrationTest extends AbstractSpringBootIntegrationTest {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    AuditPartitionRetentionIntegrationTest(JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ObjectProvider<CacheManager> cacheManagers) {
        super(cacheManagers);
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    @DisplayName("drops a fully expired partition when its rows form a chain prefix")
    void dropsFullyExpiredPartition() {
        inRolledBackTransaction(() -> {
            partitionAuditLogs();
            String old1 = insertAudit(1, 1, "old-1", "2020-01-01T00:00:00Z", "");
            String old2 = insertAudit(2, 2, "old-2", "2020-06-01T00:00:00Z", old1);
            String new1 = insertAudit(3, 3, "new-1", "2026-06-01T00:00:00Z", old2);
            String new2 = insertAudit(4, 4, "new-2", "2026-07-01T00:00:00Z", new1);
            setChainHead(4, new2);

            int deleted = retain();

            assertThat(deleted).as("rows in the dropped partition are reported").isEqualTo(2);
            assertThat(partitionExists("audit_logs_p_old")).isFalse();
            assertThat(countRows("audit_logs")).isEqualTo(2);
            assertThat(firstBrokenChainSeq()).isNull();
        });
    }

    @Test
    @DisplayName("keeps an old partition when a backdated row breaks the chain prefix")
    void keepsPartitionWhenBackdatedRowBreaksPrefix() {
        inRolledBackTransaction(() -> {
            partitionAuditLogs();
            String old1 = insertAudit(1, 1, "old-1", "2020-01-01T00:00:00Z", "");
            String old2 = insertAudit(2, 2, "old-2", "2020-06-01T00:00:00Z", old1);
            String new1 = insertAudit(3, 3, "new-1", "2026-06-01T00:00:00Z", old2);
            String new2 = insertAudit(4, 4, "new-2", "2026-07-01T00:00:00Z", new1);
            String backdated = insertAudit(5, 5, "old-backdated", "2020-12-31T00:00:00Z", new2);
            setChainHead(5, backdated);

            int deleted = retain();

            assertThat(deleted)
                    .as("the V67 prefix DELETE removes only the old rows below the first retained seq")
                    .isEqualTo(2);
            assertThat(partitionExists("audit_logs_p_old"))
                    .as("the old partition still holds the backdated high-sequence row")
                    .isTrue();
            assertThat(countRows("audit_logs_p_old")).isEqualTo(1);
            assertThat(countRows("audit_logs")).isEqualTo(3);
            assertThat(firstBrokenChainSeq()).isNull();
        });
    }

    private void partitionAuditLogs() {
        jdbcTemplate.execute("ALTER TABLE audit_logs RENAME TO audit_logs_flat_v69_test");
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id BIGINT NOT NULL,
                    "timestamp" TIMESTAMPTZ NOT NULL,
                    details TEXT NOT NULL,
                    action "AuditAction" NOT NULL,
                    username VARCHAR(255) NOT NULL,
                    actor_user_id BIGINT,
                    chain_seq BIGINT NOT NULL,
                    prev_hash VARCHAR(64) NOT NULL,
                    row_hash VARCHAR(64) NOT NULL
                ) PARTITION BY RANGE ("timestamp")
                """);
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs_p_old PARTITION OF audit_logs
                    FOR VALUES FROM ('2019-01-01') TO ('2021-01-01')
                """);
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs_p_current PARTITION OF audit_logs
                    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01')
                """);
        jdbcTemplate.execute("CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT");
    }

    private String insertAudit(long id, long chainSeq, String details, String timestamp,
            String prevHash) {
        String rowHash = jdbcTemplate.queryForObject("""
                SELECT audit_logs_row_hash(?, CAST(? AS TEXT), ?, CAST(? AS TIMESTAMPTZ), NULL, ?)
                """, String.class, "chain-test", "ACCOUNT_CREATED", details, timestamp, prevHash);
        jdbcTemplate.update("""
                INSERT INTO audit_logs
                    (id, "timestamp", details, action, username, chain_seq, prev_hash, row_hash)
                VALUES (?, CAST(? AS TIMESTAMPTZ), ?, CAST(? AS "AuditAction"), ?, ?, ?, ?)
                """, id, timestamp, details, "ACCOUNT_CREATED", "chain-test",
                chainSeq, prevHash, rowHash);
        return rowHash;
    }

    private int retain() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(365);
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT delete_audit_logs_before(?, ?)", Integer.class, cutoff, 1000);
        return deleted == null ? 0 : deleted;
    }

    private void setChainHead(long seq, String hash) {
        jdbcTemplate.update("UPDATE audit_chain_state SET last_seq = ?, last_hash = ? WHERE id = 1",
                seq, hash);
    }

    private boolean partitionExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + name));
    }

    private int countRows(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private Long firstBrokenChainSeq() {
        return jdbcTemplate.queryForObject("SELECT audit_logs_first_broken_chain_seq()", Long.class);
    }

    private void inRolledBackTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> {
            work.run();
            status.setRollbackOnly();
        });
    }
}
