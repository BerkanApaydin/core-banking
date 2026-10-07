package com.bank.app.transfer.adapter.out.persistence;

import com.bank.app.common.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

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
    void shouldReturnPageRowsWithTotalInOneQuery() {
        List<Object[]> rows = repo.findHistoryPage(201L, 10, 0);

        assertEquals(3, rows.size());
        // Newest first.
        assertEquals(30.00, ((TransferJpaEntity) rows.get(0)[0]).getAmount().doubleValue());
        assertEquals(10.00, ((TransferJpaEntity) rows.get(2)[0]).getAmount().doubleValue());
        // Same total repeated on every row.
        rows.forEach(row -> assertEquals(3L, ((Number) row[1]).longValue()));
    }

    @Test
    void shouldRespectLimitAndOffsetWithStableTotal() {
        List<Object[]> page = repo.findHistoryPage(201L, 2, 0);
        assertEquals(2, page.size());
        assertEquals(3L, ((Number) page.get(0)[1]).longValue());

        List<Object[]> remainder = repo.findHistoryPage(201L, 2, 2);
        assertEquals(1, remainder.size());
        assertEquals(10.00, ((TransferJpaEntity) remainder.get(0)[0]).getAmount().doubleValue());
        assertEquals(3L, ((Number) remainder.get(0)[1]).longValue());
    }

    @Test
    void shouldReturnEmptyPageWithZeroTotalForUnknownAccount() {
        List<Object[]> rows = repo.findHistoryPage(999L, 10, 0);

        assertTrue(rows.isEmpty());
    }
}
