package com.bank.app.account.infrastructure.adapter;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.AccountJpaRepository;
import com.bank.app.account.infrastructure.persistence.AccountMapper;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountPersistenceAdapterTest {

    @Mock private AccountJpaRepository springDataRepo;

    private AccountMapper mapper;
    private AccountPersistenceAdapter repository;

    @BeforeEach
    void setUp() {
        mapper = new AccountMapper();
        repository = new AccountPersistenceAdapter(springDataRepo, mapper);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveNewAccount() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(null, 100L, iban, "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity savedEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE");

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
        Account domainAccount = new Account(1L, 100L, iban, "Ahmet", Money.of("2000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity existingEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE", 0L);

        when(springDataRepo.findById(1L)).thenReturn(Optional.of(existingEntity));

        Account result = repository.save(domainAccount);

        assertNotNull(result);
        assertEquals("Ahmet", result.getOwnerName());
        assertEquals(new BigDecimal("2000.00"), result.getBalance().amount());
        verify(springDataRepo).findById(1L);
        verify(springDataRepo, never()).save(any());
    }

    @Test
    void shouldThrowWhenSavingExistingAccountNotFound() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(999L, 100L, iban, "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);

        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> repository.save(domainAccount));
        verify(springDataRepo).findById(999L);
    }

    @Test
    void shouldNotSaveWhenAccountIsNull() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        verifyNoInteractions(springDataRepo);
    }
}
