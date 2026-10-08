package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.account.domain.exception.AccountNotFoundException;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountPersistenceAdapterTest {

    @Mock private AccountJpaRepository springDataRepo;

    private final AccountJpaMapper mapper = new AccountJpaMapper();

    private AccountPersistenceAdapter repository;

    @BeforeEach
    void setUp() {
        repository = new AccountPersistenceAdapter(springDataRepo, mapper);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveNewAccount() {
        Iban iban = new Iban("TR770006200000000000000111");
        Account domainAccount = new Account(null, new UserId(100L), iban, "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity savedEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, null);

        when(springDataRepo.save(any(AccountJpaEntity.class))).thenReturn(savedEntity);

        Account result = repository.save(domainAccount);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Ahmet", result.getOwnerName());
        assertEquals(new BigDecimal("1000.00"), result.getBalance().amount());
        verify(springDataRepo).save(any(AccountJpaEntity.class));
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveExistingAccount() {
        Iban iban = new Iban("TR770006200000000000000111");
        Account domainAccount = new Account(1L, new UserId(100L), iban, "Ahmet", Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE, 1L);

        // Perf-1 bulk path: single versioned UPDATE, no SELECT, bumped version returned.
        when(springDataRepo.updateIfVersionMatch(1L, 1L, new BigDecimal("2000.00"), AccountStatus.ACTIVE, "Ahmet"))
                .thenReturn(1);

        Account result = repository.save(domainAccount);

        assertNotNull(result);
        assertEquals("Ahmet", result.getOwnerName());
        assertEquals(new BigDecimal("2000.00"), result.getBalance().amount());
        assertEquals(2L, result.getVersion());
        verify(springDataRepo).updateIfVersionMatch(1L, 1L, new BigDecimal("2000.00"), AccountStatus.ACTIVE, "Ahmet");
        verify(springDataRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Test
    @DisplayName("7.1: should reject save on stale version without touching the database row")
    void shouldRejectStaleVersionOnSave() {
        Iban iban = new Iban("TR770006200000000000000111");
        Account domainAccount = new Account(1L, new UserId(100L), iban, "Ahmet", Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE, 1L);
        AccountJpaEntity managedEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, 2L);

        when(springDataRepo.updateIfVersionMatch(1L, 1L, new BigDecimal("2000.00"), AccountStatus.ACTIVE, "Ahmet"))
                .thenReturn(0);
        when(springDataRepo.findById(1L)).thenReturn(Optional.of(managedEntity));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> repository.save(domainAccount));
        verify(springDataRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Test
    @DisplayName("7.1: should throw AccountNotFoundException when the row is gone")
    void shouldThrowWhenRowMissingOnSave() {
        Iban iban = new Iban("TR770006200000000000000111");
        Account domainAccount = new Account(1L, new UserId(100L), iban, "Ahmet", Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE, 1L);

        when(springDataRepo.updateIfVersionMatch(1L, 1L, new BigDecimal("2000.00"), AccountStatus.ACTIVE, "Ahmet"))
                .thenReturn(0);
        when(springDataRepo.findById(1L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class,
                () -> repository.save(domainAccount));
        verify(springDataRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Test
    void shouldNotSaveWhenAccountIsNull() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        verifyNoInteractions(springDataRepo);
    }

    @Test
    @DisplayName("legacy path: should throw not-found for version-less missing row")
    void shouldThrowNotFoundForVersionLessMissingRow() {
        // Kills the NullReturnVals mutant on the legacy orElseThrow lambda.
        Iban iban = new Iban("TR770006200000000000000111");
        Account domainAccount = new Account(9L, new UserId(100L), iban, "Ahmet",
                Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE);
        when(springDataRepo.findById(9L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> repository.save(domainAccount));
        verify(springDataRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Test
    @DisplayName("legacy path: should reject blind write for version-less existing row")
    void shouldRejectBlindWriteForVersionLessExistingRow() {
        Iban iban = new Iban("TR770006200000000000000111");
        Account domainAccount = new Account(9L, new UserId(100L), iban, "Ahmet",
                Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity managed = new AccountJpaEntity(9L, 100L, iban.value(), "Ahmet",
                new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, 2L);
        when(springDataRepo.findById(9L)).thenReturn(Optional.of(managed));

        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> repository.save(domainAccount));
        verify(springDataRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should return empty when id is null")
        void shouldReturnEmptyWhenIdIsNull() {
            Optional<Account> result = repository.findById(null);

            assertTrue(result.isEmpty());
            verifyNoInteractions(springDataRepo);
        }

        @Test
        @DisplayName("should return account when found")
        void shouldReturnAccountWhenFound() {
            AccountJpaEntity entity = new AccountJpaEntity(1L, 100L, "TR770006200000000000000111",
                    "Ahmet", new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, null);
            when(springDataRepo.findById(1L)).thenReturn(Optional.of(entity));

            Optional<Account> result = repository.findById(1L);

            assertTrue(result.isPresent());
            assertEquals("Ahmet", result.get().getOwnerName());
            assertEquals(Currency.TRY, result.get().getBalance().currency());
            verify(springDataRepo).findById(1L);
        }
    }

    @Nested
    @DisplayName("findByIds")
    class FindByIds {

        @Test
        @DisplayName("should return empty list when ids is null")
        void shouldReturnEmptyListWhenIdsIsNull() {
            List<Account> result = repository.findByIds(null);

            assertTrue(result.isEmpty());
            verifyNoInteractions(springDataRepo);
        }

        @Test
        @DisplayName("should return empty list when ids is empty")
        void shouldReturnEmptyListWhenIdsIsEmpty() {
            List<Account> result = repository.findByIds(List.of());

            assertTrue(result.isEmpty());
            verifyNoInteractions(springDataRepo);
        }

        @Test
        @DisplayName("should return accounts when ids are found")
        void shouldReturnAccountsWhenFound() {
            AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR770006200000000000000111",
                    "Ahmet", new BigDecimal("1000.00"), Currency.TRY, AccountStatus.ACTIVE, null);
            AccountJpaEntity entity2 = new AccountJpaEntity(2L, 100L, "TR870006200000000000000222",
                    "Mehmet", new BigDecimal("500.00"), Currency.USD, AccountStatus.ACTIVE, null);
            when(springDataRepo.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));

            List<Account> result = repository.findByIds(List.of(1L, 2L));

            assertEquals(2, result.size());
            assertEquals("Ahmet", result.get(0).getOwnerName());
            assertEquals("Mehmet", result.get(1).getOwnerName());
            verify(springDataRepo).findByIdIn(List.of(1L, 2L));
        }
    }

    @Nested
    @DisplayName("read-model projections")
    class Projections {

        @Test
        @DisplayName("should map info projection by id without hydrating the aggregate")
        void shouldMapInfoById() {
            when(springDataRepo.findInfoById(1L)).thenReturn(List.<Object[]>of(
                    new Object[]{1L, 100L, Currency.TRY, AccountStatus.SUSPENDED}));

            var info = repository.findInfoById(1L);

            assertTrue(info.isPresent());
            assertEquals(1L, info.get().id());
            assertEquals(100L, info.get().userId());
            assertEquals("TRY", info.get().currency());
            assertEquals("SUSPENDED", info.get().status());
        }

        @Test
        @DisplayName("should return empty for null id without touching the database")
        void shouldReturnEmptyForNullId() {
            assertTrue(repository.findInfoById(null).isEmpty());
            verifyNoInteractions(springDataRepo);
        }

        @Test
        @DisplayName("should map string-labeled enum projections")
        void shouldMapStringLabeledProjections() {
            // Kills the enumName mutants: Hibernate may materialize projected
            // native enums as labels (String) instead of enum constants. A
            // negated instanceof mutant throws ClassCastException here; an
            // EmptyObject mutant returns "" instead of the label.
            when(springDataRepo.findInfoById(1L)).thenReturn(List.<Object[]>of(
                    new Object[]{1L, 100L, "TRY", "SUSPENDED"}));

            var info = repository.findInfoById(1L);

            assertTrue(info.isPresent());
            assertEquals("TRY", info.get().currency());
            assertEquals("SUSPENDED", info.get().status());
        }

        @Test
        @DisplayName("should map info projection by iban")
        void shouldMapInfoByIban() {
            Iban iban = new Iban("TR770006200000000000000111");
            when(springDataRepo.findInfoByIban("TR770006200000000000000111")).thenReturn(List.<Object[]>of(
                    new Object[]{1L, 100L, Currency.TRY, AccountStatus.ACTIVE}));

            var info = repository.findInfoByIban(iban);

            assertTrue(info.isPresent());
            assertEquals(1L, info.get().id());
            verify(springDataRepo).findInfoByIban("TR770006200000000000000111");
        }

        @Test
        @DisplayName("should map id-to-iban pairs without hydrating aggregates")
        void shouldMapIbansByIds() {
            when(springDataRepo.findIbansByIds(List.of(1L, 2L))).thenReturn(List.<Object[]>of(
                    new Object[]{1L, "TR770006200000000000000111"},
                    new Object[]{2L, "TR870006200000000000000222"}));

            var ibans = repository.findIbansByIds(List.of(1L, 2L));

            assertEquals(2, ibans.size());
            assertEquals("TR770006200000000000000111", ibans.get(1L));
            assertEquals("TR870006200000000000000222", ibans.get(2L));
        }

        @Test
        @DisplayName("should return empty map for null or empty ids without touching the database")
        void shouldReturnEmptyMapForNullIds() {
            assertTrue(repository.findIbansByIds(null).isEmpty());
            assertTrue(repository.findIbansByIds(List.of()).isEmpty());
            verifyNoInteractions(springDataRepo);
        }
    }
}

