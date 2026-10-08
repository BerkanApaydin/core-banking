package com.bank.app.persistence;

import com.bank.app.common.AbstractIntegrationTest;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.transfer.adapter.out.persistence.TransferJpaMapper;
import com.bank.app.transfer.adapter.out.persistence.TransferPersistenceAdapter;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import com.bank.app.user.adapter.out.persistence.UserJpaMapper;
import com.bank.app.user.adapter.out.persistence.UserPersistenceAdapter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises detached domain snapshots against PostgreSQL, not mocked JPA versions. */
@Import({TransferPersistenceAdapter.class, TransferJpaMapper.class,
        UserPersistenceAdapter.class, UserJpaMapper.class})
class AggregateVersioningIntegrationTest extends AbstractIntegrationTest {
    private final EntityManager entityManager;
    private final TransferPersistenceAdapter transfers;
    private final UserPersistenceAdapter users;

    @Autowired
    AggregateVersioningIntegrationTest(EntityManager entityManager, TransferPersistenceAdapter transfers,
            UserPersistenceAdapter users) {
        this.entityManager = entityManager;
        this.transfers = transfers;
        this.users = users;
    }

    @BeforeEach
    void seed() {
        // Child-first incl. ledger_entries (V29 FK): cleanup order must never
        // depend on test execution order.
        entityManager.createNativeQuery("DELETE FROM ledger_entries").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM transfers").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM accounts").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO users (id, username, password, role, version, created_at)
                VALUES (101, 'version-test', '$2a$12$testencodedhash000000000000000000000001', 'ROLE_USER', 0, NOW())
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO accounts (id, user_id, iban, owner_name, balance, currency, status, version, created_at)
                VALUES (201, 101, 'TR770006200000000000000111', 'Sender', 100, 'TRY', 'ACTIVE', 0, NOW()),
                       (202, 101, 'TR870006200000000000000222', 'Receiver', 100, 'TRY', 'ACTIVE', 0, NOW())
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO transfers (id, sender_account_id, receiver_account_id, amount, currency, status, version, created_at, business_created_at)
                VALUES (301, 201, 202, 10, 'TRY', 'PENDING', 0, NOW(), NOW())
                """).executeUpdate();
    }

    @Test
    void rejectsStaleTransferInsteadOfResurrectingFailedTransfer() {
        Transfer stale = transfers.findById(301L).orElseThrow();
        entityManager.createNativeQuery("UPDATE transfers SET status = 'FAILED', version = 1 WHERE id = 301")
                .executeUpdate();
        entityManager.clear();
        stale.complete(Clock.systemUTC());

        assertThrows(OptimisticLockingFailureException.class, () -> {
            transfers.save(stale);
            entityManager.flush();
        });
    }

    @Test
    void rejectsStaleUserInsteadOfErasingNewContactDetails() {
        var stale = users.findByUsername("version-test").orElseThrow();
        entityManager.createNativeQuery("UPDATE users SET email = 'new@example.com', version = 1 WHERE id = 101")
                .executeUpdate();
        entityManager.clear();
        stale.changePassword("$2a$12$testencodedhash000000000000000000000002");

        assertThrows(OptimisticLockingFailureException.class, () -> {
            users.save(stale);
            entityManager.flush();
        });
    }

    @Test
    void rejectsMissingVersionForExistingTransfer() {
        var unversioned = new Transfer(301L, 201L, 202L, Money.of("10", Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now());
        assertThrows(OptimisticLockingFailureException.class, () -> {
            transfers.save(unversioned);
            entityManager.flush();
        });
    }

    @Test
    void rejectsMissingVersionForExistingUser() {
        var unversioned = new User(
                new UserId(101L), "version-test", "$2a$12$testencodedhash000000000000000000000001",
                Role.ROLE_USER);
        assertThrows(OptimisticLockingFailureException.class, () -> users.save(unversioned));
    }

    @Test
    void matchingUserVersionPreservesContactDetailsAndAdvancesVersion() {
        var user = users.findByUsername("version-test").orElseThrow();
        user.updateEmail(new EmailAddress("new@example.com"));
        users.save(user);
        entityManager.flush();
        entityManager.clear();
        var reloaded = users.findByUsername("version-test").orElseThrow();
        assertEquals("new@example.com", reloaded.getEmail().value());
        assertEquals(1L, reloaded.getVersion());
    }

    @Test
    void matchingVersionAllowsUpdateAndHibernateAdvancesVersion() {
        Transfer transfer = transfers.findById(301L).orElseThrow();
        transfer.complete(Clock.systemUTC());
        transfers.save(transfer);
        entityManager.flush();
        entityManager.clear();

        Transfer reloaded = transfers.findById(301L).orElseThrow();
        assertEquals(TransferStatus.COMPLETED, reloaded.getStatus());
        assertEquals(1L, reloaded.getVersion());
    }
}
