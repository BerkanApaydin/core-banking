package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JpaAccountRepositoryTest {

    private SpringDataAccountRepo springDataRepo;
    private AccountMapper mapper;
    private JpaAccountRepository repository;

    @BeforeEach
    void setUp() {
        springDataRepo = mock(SpringDataAccountRepo.class);
        mapper = new AccountMapper();
        repository = new JpaAccountRepository(springDataRepo, mapper, mock(jakarta.persistence.EntityManager.class));
    }

    @Test
    void shouldFindByIbanSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", true);

        when(springDataRepo.findByIban(iban.value())).thenReturn(Optional.of(jpaEntity));

        Optional<Account> result = repository.findByIban(iban);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        assertEquals(new BigDecimal("1000.00"), result.get().getBalance().amount());
        verify(springDataRepo).findByIban(iban.value());
    }

    @Test
    void shouldFindByIdSuccessfully() {
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "TRY", true);

        when(springDataRepo.findById(1L)).thenReturn(Optional.of(jpaEntity));

        Optional<Account> result = repository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        verify(springDataRepo).findById(1L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(1L, 100L, iban, "Ahmet", Money.of("1000.00", Money.Currency.TRY), true);

        repository.save(domainAccount);

        verify(springDataRepo).save(any(AccountJpaEntity.class));
    }
}
