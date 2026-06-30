package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
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

import java.math.BigDecimal;
import java.util.List;
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
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(null, new UserId(100L), iban, "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity savedEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE", null);

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
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(1L, new UserId(100L), iban, "Ahmet", Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity savedEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("2000.00"), "TRY", "ACTIVE", 1L);

        when(springDataRepo.save(any(AccountJpaEntity.class))).thenReturn(savedEntity);

        Account result = repository.save(domainAccount);

        assertNotNull(result);
        assertEquals("Ahmet", result.getOwnerName());
        assertEquals(new BigDecimal("2000.00"), result.getBalance().amount());
        verify(springDataRepo).save(any(AccountJpaEntity.class));
    }

    @Test
    void shouldNotSaveWhenAccountIsNull() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        verifyNoInteractions(springDataRepo);
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
            AccountJpaEntity entity = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111",
                    "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE", null);
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
            AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111",
                    "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE", null);
            AccountJpaEntity entity2 = new AccountJpaEntity(2L, 100L, "TR290006200000000000000222",
                    "Mehmet", new BigDecimal("500.00"), "USD", "ACTIVE", null);
            when(springDataRepo.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));

            List<Account> result = repository.findByIds(List.of(1L, 2L));

            assertEquals(2, result.size());
            assertEquals("Ahmet", result.get(0).getOwnerName());
            assertEquals("Mehmet", result.get(1).getOwnerName());
            verify(springDataRepo).findByIdIn(List.of(1L, 2L));
        }
    }
}

