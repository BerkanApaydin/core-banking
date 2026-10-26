package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Persistence failure classification on account creation.
 *
 * <p>The unique IBAN constraint is the only race-safe duplicate guard: a
 * concurrent create must surface as a 409-style domain conflict, while every
 * unrelated integrity error must keep its 500 semantics. Driver messages vary
 * across PostgreSQL versions and are sometimes reachable only through a deep
 * cause chain, so the classifier must walk it (bounded, because
 * {@code initCause} permits cycles) instead of inspecting the top exception
 * only.
 */
@ExtendWith(MockitoExtension.class)
class AccountPersistenceAdapterDuplicateIbanEdgeCaseTest {

    private static final Iban IBAN = new Iban("TR770006200000000000000111");

    @Mock
    private AccountJpaRepository repository;

    private AccountPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountPersistenceAdapter(repository, new AccountJpaMapper());
    }

    private static Account newAccount() {
        return Account.rehydrate(null, new UserId(100L), IBAN, "Ahmet",
                Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
    }

    private void saveFailsWith(DataIntegrityViolationException failure) {
        when(repository.save(any(AccountJpaEntity.class))).thenThrow(failure);
    }

    @Test
    void shouldTranslatePostgresUniqueStateNestedDeepInCauseChain() {
        SQLException unique = new SQLException("duplicate key", "23505");
        RuntimeException middle = new RuntimeException("wrapped", unique);
        saveFailsWith(new DataIntegrityViolationException("constraint failed", middle));

        assertThrows(DuplicateIbanException.class, () -> adapter.save(newAccount()));
    }

    @Test
    void shouldTranslateConstraintNameInNestedMessage() {
        RuntimeException middle = new RuntimeException(
                "ERROR: duplicate key value violates unique constraint \"accounts_iban_key\"");
        saveFailsWith(new DataIntegrityViolationException("constraint failed", middle));

        assertThrows(DuplicateIbanException.class, () -> adapter.save(newAccount()));
    }

    @Test
    void shouldTranslateLowercaseDuplicateIbanMessage() {
        saveFailsWith(new DataIntegrityViolationException(
                "duplicate entry for iban value"));

        assertThrows(DuplicateIbanException.class, () -> adapter.save(newAccount()));
    }

    @Test
    void shouldTranslateUniqueConstraintOnIbanMessage() {
        saveFailsWith(new DataIntegrityViolationException(
                "UNIQUE constraint failed: accounts.iban"));

        assertThrows(DuplicateIbanException.class, () -> adapter.save(newAccount()));
    }

    @Test
    void shouldPropagateUnrelatedIntegrityFailureUnchanged() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("connection reset by peer");
        saveFailsWith(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> adapter.save(newAccount()));
        org.junit.jupiter.api.Assertions.assertSame(failure, thrown);
    }

    @Test
    void shouldNotTreatAnIbanWordWithoutUniquenessAsDuplicate() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("iban formatting failed");
        saveFailsWith(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> adapter.save(newAccount()));
        org.junit.jupiter.api.Assertions.assertSame(failure, thrown);
    }

    @Test
    void shouldNotHangOrMisclassifyACyclicCauseChain() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("wrapper", first);
        saveFailsWith(failure);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThrows(DataIntegrityViolationException.class,
                        () -> adapter.save(newAccount())));
    }

    @Test
    void shouldNotClassifyAUniqueViolationBeyondTheBoundedCauseDepth() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("wrapper");
        Throwable current = failure;
        for (int depth = 1; depth < 16; depth++) {
            RuntimeException next = new RuntimeException("level-" + depth);
            current.initCause(next);
            current = next;
        }
        current.initCause(new SQLException("duplicate key", "23505"));
        saveFailsWith(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> adapter.save(newAccount()));
        org.junit.jupiter.api.Assertions.assertSame(failure, thrown);
    }

    @Test
    void shouldStopWalkingAtASelfReferentialCause() {
        Throwable selfCausing = new RuntimeException("self-referential") {
            private static final long serialVersionUID = 1L;

            @Override
            public Throwable getCause() {
                return this;
            }
        };
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("wrapper", selfCausing);
        saveFailsWith(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> adapter.save(newAccount()));
        org.junit.jupiter.api.Assertions.assertSame(failure, thrown);
    }

    @Test
    void shouldClassifyUniqueViolationWhenOuterMessageIsNull() {
        SQLException unique = new SQLException("duplicate key", "23505");
        saveFailsWith(new DataIntegrityViolationException(null, unique));

        assertThrows(DuplicateIbanException.class, () -> adapter.save(newAccount()));
    }

    @Test
    void shouldPropagateANullMessageFailureWithoutACause() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(null);
        saveFailsWith(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> adapter.save(newAccount()));
        org.junit.jupiter.api.Assertions.assertSame(failure, thrown);
    }
}
