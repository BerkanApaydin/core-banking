package com.bank.app.account.adapter.out.persistence;

import com.bank.app.common.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
@Transactional
class AccountJpaRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountJpaRepository repo;

    @Autowired
    private EntityManager entityManager;

    private Long savedEntityId;

    @BeforeEach
    void setUp() {
        // Delete child-first: transfers reference accounts (and the FK graph
        // may exist on databases migrated before V22 dropped the constraints),
        // so cleanup order must never depend on test execution order.
        entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO users (id, username, password, role, created_at) " +
                "VALUES (100, 'test_user_db', 'pass', 'ROLE_USER', NOW())")
                .executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, status, version, created_at) " +
                "VALUES (2, 100, 'TR770006200000000000000111', 'Test User', 1000.00, 'TRY', 'ACTIVE', 0, NOW())")
                .executeUpdate();

        savedEntityId = 2L;
    }

    @Test
    void shouldSaveAndFindById() {
        Optional<AccountJpaEntity> found = repo.findById(savedEntityId);
        assertTrue(found.isPresent());
        assertEquals("Test User", found.get().getOwnerName());
        assertEquals("TR770006200000000000000111", found.get().getIban());
    }

    @Test
    void shouldFindByIban() {
        Optional<AccountJpaEntity> found = repo.findByIban("TR770006200000000000000111");
        assertTrue(found.isPresent());
        assertEquals(savedEntityId, found.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenIbanNotFound() {
        Optional<AccountJpaEntity> found = repo.findByIban("TR600006200000000000000999");
        assertFalse(found.isPresent());
    }

    @Test
    void shouldFindByIbanWithPessimisticLock() {
        Optional<AccountJpaEntity> locked = repo.findByIbanForUpdate("TR770006200000000000000111");
        assertTrue(locked.isPresent());
        assertEquals(savedEntityId, locked.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenIbanForUpdateNotFound() {
        Optional<AccountJpaEntity> locked = repo.findByIbanForUpdate("TR600006200000000000000999");
        assertFalse(locked.isPresent());
    }

    @Test
    void shouldFindByIdWithPessimisticLock() {
        Optional<AccountJpaEntity> locked = repo.findByIdForUpdate(savedEntityId);
        assertTrue(locked.isPresent());
        assertEquals(savedEntityId, locked.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenFindByIdForUpdateNotFound() {
        Optional<AccountJpaEntity> locked = repo.findByIdForUpdate(999L);
        assertFalse(locked.isPresent());
    }

    @Test
    void shouldFindByUserId() {
        Page<AccountJpaEntity> results = repo.findByUserIdOrderByCreatedAtDescIdDesc(100L, Pageable.unpaged());
        assertEquals(1, results.getContent().size());
    }

    @Test
    void shouldUseIdToBreakCreatedAtTiesAcrossPages() {
        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, status, version, created_at) " +
                "SELECT 3, user_id, 'TR870006200000000000000222', owner_name, balance, currency, status, version, created_at " +
                "FROM accounts WHERE id = 2")
                .executeUpdate();

        var first = repo.findByUserIdOrderByCreatedAtDescIdDesc(100L,
                org.springframework.data.domain.PageRequest.of(0, 1));
        var second = repo.findByUserIdOrderByCreatedAtDescIdDesc(100L,
                org.springframework.data.domain.PageRequest.of(1, 1));

        assertEquals(3L, first.getContent().getFirst().getId());
        assertEquals(2L, second.getContent().getFirst().getId());
    }

    @Test
    void shouldReturnEmptyListWhenUserIdNotFound() {
        Page<AccountJpaEntity> results = repo.findByUserIdOrderByCreatedAtDescIdDesc(999L, Pageable.unpaged());
        assertTrue(results.getContent().isEmpty());
    }

    @Test
    void shouldPersistAllAccountFieldsCorrectly() {
        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, status, version, created_at) " +
                "VALUES (3, 100, 'TR870006200000000000000222', 'Another User', 500.50, 'EUR', 'SUSPENDED', 0, NOW())")
                .executeUpdate();

        Optional<AccountJpaEntity> found = repo.findByIban("TR870006200000000000000222");
        assertTrue(found.isPresent());
        assertEquals(100L, found.get().getUserId());
        assertEquals("TR870006200000000000000222", found.get().getIban());
        assertEquals("Another User", found.get().getOwnerName());
        assertEquals(0, new BigDecimal("500.50").compareTo(found.get().getBalance()));
        assertEquals("EUR", found.get().getCurrency());
        assertEquals("SUSPENDED", found.get().getStatus());
    }

    @Test
    void shouldUpdateAccountBalance() {
        Optional<AccountJpaEntity> toUpdate = repo.findById(savedEntityId);
        assertTrue(toUpdate.isPresent());
        toUpdate.get().setBalance(new BigDecimal("2000.00"));

        entityManager.flush();

        Optional<AccountJpaEntity> found = repo.findById(savedEntityId);
        assertTrue(found.isPresent());
        assertEquals(0, new BigDecimal("2000.00").compareTo(found.get().getBalance()));
    }
}
