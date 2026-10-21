package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the window-function history query returns page rows plus the exact
 * total in a single round trip, newest first, for both sender and receiver
 * sides of an account.
 */
@SuppressWarnings("null")
@Transactional
class TransferHistoryPageIntegrationTest extends AbstractIntegrationTest {

    private final TransferJpaRepository repo;

    private final EntityManager entityManager;

    @Autowired
    TransferHistoryPageIntegrationTest(TransferJpaRepository repo, EntityManager entityManager) {
        this.repo = repo;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        // Child-first so cleanup never depends on test execution order.
        entityManager.createNativeQuery("DELETE FROM ledger_entries").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO users (id, username, password, role, created_at) " +
                "VALUES (100, 'history_user', 'pass', 'ROLE_USER', NOW())")
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, status, version, created_at) VALUES " +
                "(201, 100, 'TR770006200000000000000111', 'Sender', 1000.00, 'TRY', 'ACTIVE', 0, NOW()), " +
                "(202, 100, 'TR870006200000000000000222', 'Receiver', 1000.00, 'TRY', 'ACTIVE', 0, NOW())")
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO transfers (sender_account_id, receiver_account_id, amount, currency, status, business_created_at, created_at) VALUES " +
                "(201, 202, 10.00, 'TRY', 'COMPLETED', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours'), " +
                "(202, 201, 20.00, 'TRY', 'COMPLETED', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'), " +
                "(201, 202, 30.00, 'TRY', 'COMPLETED', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour')")
                .executeUpdate();
    }

    @Test
    void shouldReturnPageRowsWithSeparateTotalCount() {
        // The windowed query (COUNT(*) OVER() over the whole union) is gone:
        // PostgreSQL can only evaluate a window function after materialising
        // and sorting the entire union, so a small page cost O(n) over the
        // account's whole transfer history. The rows and the exact total now
        // come from two cheap statements.
        List<TransferJpaEntity> rows = repo.findHistoryRows(201L, 10, 0);

        assertEquals(3, rows.size());
        // Newest first.
        assertEquals(30.00, rows.get(0).getAmount().doubleValue());
        assertEquals(10.00, rows.get(2).getAmount().doubleValue());
        assertEquals(3L, repo.countHistory(201L));
    }

    @Test
    void shouldRespectLimitAndOffsetWithStableTotal() {
        List<TransferJpaEntity> page = repo.findHistoryRows(201L, 2, 0);
        assertEquals(2, page.size());
        assertEquals(3L, repo.countHistory(201L));

        List<TransferJpaEntity> remainder = repo.findHistoryRows(201L, 2, 2);
        assertEquals(1, remainder.size());
        assertEquals(10.00, remainder.get(0).getAmount().doubleValue());
        assertEquals(3L, repo.countHistory(201L));
    }

    @Test
    void shouldPageReportsByBusinessTimeWhenInsertOrderDiffers() {
        entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
        // Business time and auditing insert time deliberately disagree:
        // newest business transfer was inserted first, oldest was inserted last.
        entityManager.createNativeQuery(
                "INSERT INTO transfers (sender_account_id, receiver_account_id, amount, currency, status, business_created_at, created_at) VALUES " +
                "(201, 202, 10.00, 'TRY', 'COMPLETED', NOW() - INTERVAL '3 hours', NOW() - INTERVAL '1 hour'), " +
                "(202, 201, 20.00, 'TRY', 'COMPLETED', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'), " +
                "(201, 202, 30.00, 'TRY', 'COMPLETED', NOW() - INTERVAL '1 hour', NOW() - INTERVAL '3 hours')")
                .executeUpdate();

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        List<TransferJpaEntity> first = repo.findHistoryBetween(201L, start, end, PageRequest.of(0, 2));
        assertEquals(2, first.size());
        assertEquals(30.00, first.get(0).getAmount().doubleValue());
        assertEquals(20.00, first.get(1).getAmount().doubleValue());

        TransferJpaEntity cursor = first.get(1);
        List<TransferJpaEntity> second = repo.findHistoryBetweenKeyset(201L, start, end,
                cursor.getBusinessCreatedAt(), cursor.getId(), PageRequest.of(0, 2));
        assertEquals(1, second.size());
        assertEquals(10.00, second.get(0).getAmount().doubleValue());
    }

    @Test
    void shouldReturnEmptyPageWithZeroTotalForUnknownAccount() {
        List<TransferJpaEntity> rows = repo.findHistoryRows(999L, 10, 0);

        assertTrue(rows.isEmpty());
        assertEquals(0L, repo.countHistory(999L));
    }

    @Test
    void shouldReturnRowsWithoutWindowForDeeperPages() {
        // P-1: rows-only query matches the windowed query's content/order.
        List<TransferJpaEntity> rows = repo.findHistoryRows(201L, 2, 0);
        assertEquals(2, rows.size());
        assertEquals(30.00, rows.get(0).getAmount().doubleValue());
        assertEquals(20.00, rows.get(1).getAmount().doubleValue());

        List<TransferJpaEntity> remainder = repo.findHistoryRows(201L, 2, 2);
        assertEquals(1, remainder.size());
        assertEquals(10.00, remainder.get(0).getAmount().doubleValue());
    }

    @Test
    void shouldCountHistorySeparately() {
        assertEquals(3L, repo.countHistory(201L));
        assertEquals(0L, repo.countHistory(999L));
    }

    @Test
    void shouldSummarizeRangeWithCountAndSumInOneScan() {
        var rows = repo.summarizeRange(201L,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertEquals(1, rows.size());
        assertEquals(3L, ((Number) rows.get(0)[0]).longValue());
        assertEquals(0, ((BigDecimal) rows.get(0)[1]).compareTo(new BigDecimal("60.00")));
    }

    @Test
    void shouldSummarizeEmptyRangeAsZero() {
        var rows = repo.summarizeRange(201L,
                LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3));

        assertEquals(1, rows.size());
        assertEquals(0L, ((Number) rows.get(0)[0]).longValue());
        assertEquals(0, ((BigDecimal) rows.get(0)[1]).compareTo(BigDecimal.ZERO));
    }
}
