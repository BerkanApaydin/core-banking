package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class AccountJpaRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountJpaRepository repo;

    @Autowired
    private EntityManager entityManager;

    private Long savedEntityId;

    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();

        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, active, version, created_at) " +
                "VALUES (2, 100, 'TR290006200000000000000111', 'Test User', 1000.00, 'TRY', true, 0, NOW())")
                .executeUpdate();

        savedEntityId = 2L;
    }

    @Test
    void shouldSaveAndFindById() {
        Optional<AccountJpaEntity> found = repo.findById(savedEntityId);
        assertTrue(found.isPresent());
        assertEquals("Test User", found.get().getOwnerName());
        assertEquals("TR290006200000000000000111", found.get().getIban());
    }

    @Test
    void shouldFindByIban() {
        Optional<AccountJpaEntity> found = repo.findByIban("TR290006200000000000000111");
        assertTrue(found.isPresent());
        assertEquals(savedEntityId, found.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenIbanNotFound() {
        Optional<AccountJpaEntity> found = repo.findByIban("TR290006200000000000000999");
        assertFalse(found.isPresent());
    }

    @Test
    void shouldFindByIbanWithPessimisticLock() {
        Optional<AccountJpaEntity> locked = repo.findByIbanWithLock("TR290006200000000000000111");
        assertTrue(locked.isPresent());
        assertEquals(savedEntityId, locked.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenIbanWithLockNotFound() {
        Optional<AccountJpaEntity> locked = repo.findByIbanWithLock("TR290006200000000000000999");
        assertFalse(locked.isPresent());
    }

    @Test
    void shouldFindByIdWithPessimisticLock() {
        Optional<AccountJpaEntity> locked = repo.findByIdWithLock(savedEntityId);
        assertTrue(locked.isPresent());
        assertEquals(savedEntityId, locked.get().getId());
    }

    @Test
    void shouldReturnEmptyWhenFindByIdWithLockNotFound() {
        Optional<AccountJpaEntity> locked = repo.findByIdWithLock(999L);
        assertFalse(locked.isPresent());
    }

    @Test
    void shouldFindByUserId() {
        List<AccountJpaEntity> results = repo.findByUserId(100L);
        assertEquals(1, results.size());
    }

    @Test
    void shouldReturnEmptyListWhenUserIdNotFound() {
        List<AccountJpaEntity> results = repo.findByUserId(999L);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldPersistAllAccountFieldsCorrectly() {
        entityManager.createNativeQuery(
                "INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, active, version, created_at) " +
                "VALUES (3, 100, 'TR290006200000000000000222', 'Another User', 500.50, 'EUR', false, 0, NOW())")
                .executeUpdate();

        Optional<AccountJpaEntity> found = repo.findByIban("TR290006200000000000000222");
        assertTrue(found.isPresent());
        assertEquals(100L, found.get().getUserId());
        assertEquals("TR290006200000000000000222", found.get().getIban());
        assertEquals("Another User", found.get().getOwnerName());
        assertEquals(0, new BigDecimal("500.50").compareTo(found.get().getBalance()));
        assertEquals("EUR", found.get().getCurrency());
        assertFalse(found.get().isActive());
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
