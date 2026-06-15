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

    @Test
    void shouldFindByIbanWithLockSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", true);

        when(springDataRepo.findByIbanWithLock(iban.value())).thenReturn(Optional.of(jpaEntity));

        var result = repository.findByIbanWithLock(iban);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        assertEquals(new BigDecimal("1000.00"), result.get().getBalance().amount());
        verify(springDataRepo).findByIbanWithLock(iban.value());
    }

    @Test
    void shouldReturnEmptyWhenFindByIbanWithLockNotFound() {
        Iban iban = new Iban("TR290006200000000000000999");

        when(springDataRepo.findByIbanWithLock(iban.value())).thenReturn(Optional.empty());

        var result = repository.findByIbanWithLock(iban);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIbanWithLock(iban.value());
    }

    @Test
    void shouldFindAllSuccessfully() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "TRY", true);
        AccountJpaEntity entity2 = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"), "TRY", true);

        when(springDataRepo.findAll()).thenReturn(java.util.List.of(entity1, entity2));

        var result = repository.findAll();

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenFindAllReturnsEmpty() {
        when(springDataRepo.findAll()).thenReturn(java.util.List.of());

        var result = repository.findAll();

        assertTrue(result.isEmpty());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldFindByIdsSuccessfully() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "TRY", true);
        AccountJpaEntity entity2 = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"), "TRY", true);

        when(springDataRepo.findAllById(java.util.List.of(1L, 2L))).thenReturn(java.util.List.of(entity1, entity2));

        var result = repository.findByIds(java.util.List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findAllById(java.util.List.of(1L, 2L));
    }

    @Test
    void shouldReturnEmptyListWhenFindByIdsIsNull() {
        var result = repository.findByIds(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(springDataRepo);
    }

    @Test
    void shouldReturnEmptyListWhenFindByIdsIsEmpty() {
        var result = repository.findByIds(java.util.List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(springDataRepo);
    }

    @Test
    @SuppressWarnings("null")
    void shouldNotSaveWhenAccountIsNull() {
        repository.save(null);
        verifyNoInteractions(springDataRepo);
    }
}
