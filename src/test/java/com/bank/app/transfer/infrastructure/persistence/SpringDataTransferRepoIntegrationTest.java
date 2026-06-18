package com.bank.app.transfer.infrastructure.persistence;

import com.bank.app.common.AbstractIntegrationTest;
import com.bank.app.transfer.domain.TransferStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class SpringDataTransferRepoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpringDataTransferRepo repo;

    @Autowired
    private EntityManager entityManager;

    private TransferJpaEntity savedEntity;
    private Long accountId1;
    private Long accountId2;

    @BeforeEach
    void setUp() {
        repo.deleteAll();
        entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO users (id, username, password, role, created_at) VALUES (100, 'testuser', 'pass', 'ROLE_USER', NOW())")
                .executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, active, created_at) VALUES (1, 100, 'TR290006200000000000000111', 'Sender', 1000.00, 'TRY', true, NOW())")
                .executeUpdate();
        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, active, created_at) VALUES (2, 100, 'TR290006200000000000000222', 'Receiver', 1000.00, 'TRY', true, NOW())")
                .executeUpdate();

        accountId1 = 1L;
        accountId2 = 2L;

        TransferJpaEntity entity = new TransferJpaEntity(
                null, accountId1, accountId2, new BigDecimal("100.00"), "TRY",
                TransferStatus.COMPLETED, LocalDateTime.now());
        savedEntity = repo.save(entity);
    }

    @Test
    void shouldSaveAndFindById() {
        Optional<TransferJpaEntity> found = repo.findById(savedEntity.getId());
        assertTrue(found.isPresent());
        assertEquals(accountId1, found.get().getSenderAccountId());
        assertEquals(accountId2, found.get().getReceiverAccountId());
        assertEquals(TransferStatus.COMPLETED, found.get().getStatus());
    }

    @Test
    void shouldFindBySenderAccountId() {
        List<TransferJpaEntity> results = repo.findBySenderAccountId(accountId1);
        assertEquals(1, results.size());
    }

    @Test
    void shouldReturnEmptyWhenSenderNotFound() {
        List<TransferJpaEntity> results = repo.findBySenderAccountId(999L);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldFindHistoryWithPageable() {
        for (int i = 0; i < 5; i++) {
            repo.save(new TransferJpaEntity(
                    null, accountId1, accountId2, new BigDecimal("50.00"), "TRY",
                    TransferStatus.COMPLETED, LocalDateTime.now().minusHours(i)));
        }
        List<TransferJpaEntity> results = repo.findHistory(accountId1, PageRequest.of(0, 3));
        assertEquals(3, results.size());
    }

    @Test
    void shouldFindHistoryBetweenDates() {
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        List<TransferJpaEntity> results = repo.findHistoryBetween(accountId1, start, end);
        assertEquals(1, results.size());
    }

    @Test
    void shouldFindHistoryBetweenDatesWithPageable() {
        for (int i = 0; i < 5; i++) {
            repo.save(new TransferJpaEntity(
                    null, accountId1, accountId2, new BigDecimal("50.00"), "TRY",
                    TransferStatus.COMPLETED, LocalDateTime.now().minusHours(i)));
        }
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);
        List<TransferJpaEntity> results = repo.findHistoryBetween(accountId1, start, end, PageRequest.of(0, 2));
        assertEquals(2, results.size());
    }

    @Test
    void shouldReturnEmptyHistoryWhenNoMatches() {
        LocalDateTime start = LocalDateTime.now().minusDays(10);
        LocalDateTime end = LocalDateTime.now().minusDays(9);
        List<TransferJpaEntity> results = repo.findHistoryBetween(accountId1, start, end);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldFindByIdWithPessimisticLock() {
        Optional<TransferJpaEntity> locked = repo.findByIdWithLock(savedEntity.getId());
        assertTrue(locked.isPresent());
        assertEquals(savedEntity.getId(), locked.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenFindByIdWithLockNotFound() {
        Optional<TransferJpaEntity> locked = repo.findByIdWithLock(999L);
        assertFalse(locked.isPresent());
    }

    @Test
    void shouldOrderHistoryByCreatedAtDesc() {
        TransferJpaEntity older = repo.save(new TransferJpaEntity(
                null, accountId1, accountId2, new BigDecimal("30.00"), "TRY",
                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(2)));
        TransferJpaEntity newer = repo.save(new TransferJpaEntity(
                null, accountId1, accountId2, new BigDecimal("70.00"), "TRY",
                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1)));

        List<TransferJpaEntity> results = repo.findHistory(accountId1, PageRequest.of(0, 10));
        assertEquals(3, results.size());
        assertTrue(results.get(0).getCreatedAt().isAfter(results.get(1).getCreatedAt())
                || results.get(0).getCreatedAt().isEqual(results.get(1).getCreatedAt()));
    }

    @Test
    void shouldPersistAllTransferFieldsCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(
                null, accountId1, accountId2, new BigDecimal("250.75"), "USD",
                TransferStatus.COMPLETED, now);
        TransferJpaEntity saved = repo.save(entity);

        Optional<TransferJpaEntity> found = repo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(accountId1, found.get().getSenderAccountId());
        assertEquals(accountId2, found.get().getReceiverAccountId());
        assertEquals(0, new BigDecimal("250.75").compareTo(found.get().getAmount()));
        assertEquals("USD", found.get().getCurrency());
        assertEquals(TransferStatus.COMPLETED, found.get().getStatus());
    }

    @Test
    void shouldUpdateTransferStatus() {
        savedEntity.setStatus(TransferStatus.CANCELLED);
        repo.save(savedEntity);

        Optional<TransferJpaEntity> found = repo.findById(savedEntity.getId());
        assertTrue(found.isPresent());
        assertEquals(TransferStatus.CANCELLED, found.get().getStatus());
    }
}
