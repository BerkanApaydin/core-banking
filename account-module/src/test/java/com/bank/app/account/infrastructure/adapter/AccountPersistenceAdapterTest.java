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
import java.util.List;
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

    private AccountJpaEntity createEntity(Long id, String iban, String ownerName, BigDecimal balance, Long userId) {
        return new AccountJpaEntity(id, userId, iban, ownerName, balance, "TRY", "ACTIVE");
    }

    @Test
    void shouldFindByIbanSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = createEntity(1L, iban.value(), "Ahmet", new BigDecimal("1000.00"), 100L);

        when(springDataRepo.findByIban(iban.value())).thenReturn(Optional.of(jpaEntity));

        Optional<Account> result = repository.findByIban(iban);

        assertTrue(result.isPresent());
        Account account = result.get();
        assertEquals("Ahmet", account.getOwnerName());
        assertEquals(1L, account.getId());
        assertEquals(100L, account.getUserId());
        assertEquals("TR290006200000000000000111", account.getIban().value());
        assertEquals(new BigDecimal("1000.00"), account.getBalance().amount());
        assertTrue(account.isActive());
        verify(springDataRepo).findByIban(iban.value());
    }

    @Test
    void shouldFindByIdSuccessfully() {
        AccountJpaEntity jpaEntity = createEntity(1L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), 100L);

        when(springDataRepo.findById(1L)).thenReturn(Optional.of(jpaEntity));

        Optional<Account> result = repository.findById(1L);

        assertTrue(result.isPresent());
        Account account = result.get();
        assertEquals(1L, account.getId());
        assertEquals("Ahmet", account.getOwnerName());
        assertEquals(100L, account.getUserId());
        assertEquals(new BigDecimal("1000.00"), account.getBalance().amount());
        verify(springDataRepo).findById(1L);
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveExistingAccountWithLock() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(1L, 100L, iban, "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity existingEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE", 0L);

        when(springDataRepo.findByIdWithLock(1L)).thenReturn(Optional.of(existingEntity));

        Account result = repository.save(domainAccount);

        assertNotNull(result);
        assertEquals("Ahmet", result.getOwnerName());
        assertEquals(new BigDecimal("1000.00"), result.getBalance().amount());
        verify(springDataRepo).findByIdWithLock(1L);
        verify(springDataRepo, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void shouldSaveNewAccount() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domainAccount = new Account(null, 100L, iban, "Ahmet", Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE);
        AccountJpaEntity savedEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE");

        when(springDataRepo.save(any(AccountJpaEntity.class))).thenReturn(savedEntity);

        repository.save(domainAccount);

        verify(springDataRepo).save(any(AccountJpaEntity.class));
    }

    @Test
    void shouldFindByIbanWithLockSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE");

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
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE");
        AccountJpaEntity entity2 = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"), "TRY", "ACTIVE");

        when(springDataRepo.findAll()).thenReturn(List.of(entity1, entity2));

        var result = repository.findAll();

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenFindAllReturnsEmpty() {
        when(springDataRepo.findAll()).thenReturn(List.of());

        var result = repository.findAll();

        assertTrue(result.isEmpty());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldFindByIdsSuccessfully() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "TRY", "ACTIVE");
        AccountJpaEntity entity2 = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"), "TRY", "ACTIVE");

        when(springDataRepo.findAllById(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));

        var result = repository.findByIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findAllById(List.of(1L, 2L));
    }

    @Test
    void shouldReturnEmptyListWhenFindByIdsIsNull() {
        var result = repository.findByIds(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(springDataRepo);
    }

    @Test
    void shouldReturnEmptyListWhenFindByIdsIsEmpty() {
        var result = repository.findByIds(List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(springDataRepo);
    }

    @Test
    void shouldNotSaveWhenAccountIsNull() {
        assertThrows(IllegalArgumentException.class, () -> repository.save(null));
        verifyNoInteractions(springDataRepo);
    }

    @Test
    void shouldFilterNullEntitiesFromFindAll() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "INVALID", "ACTIVE");

        when(springDataRepo.findAll()).thenReturn(List.of(entity1));

        var result = repository.findAll();

        assertTrue(result.isEmpty());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldReturnEmptyWhenFindByIbanNotFound() {
        Iban iban = new Iban("TR290006200000000000000999");

        when(springDataRepo.findByIban(iban.value())).thenReturn(Optional.empty());

        Optional<Account> result = repository.findByIban(iban);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIban(iban.value());
    }

    @Test
    void shouldReturnEmptyWhenFindByIdNotFound() {
        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        Optional<Account> result = repository.findById(999L);

        assertFalse(result.isPresent());
        verify(springDataRepo).findById(999L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdWithLockNotFound() {
        when(springDataRepo.findByIdWithLock(999L)).thenReturn(Optional.empty());

        var result = repository.findByIdWithLock(999L);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIdWithLock(999L);
    }

    @Test
    void shouldFindByUserIdSuccessfully() {
        AccountJpaEntity entity1 = createEntity(1L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), 100L);
        AccountJpaEntity entity2 = createEntity(2L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"), 100L);

        when(springDataRepo.findByUserId(100L)).thenReturn(List.of(entity1, entity2));

        var result = repository.findByUserId(100L);

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findByUserId(100L);
    }

    @Test
    void shouldReturnEmptyListWhenFindByUserIdReturnsEmpty() {
        when(springDataRepo.findByUserId(999L)).thenReturn(List.of());

        var result = repository.findByUserId(999L);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findByUserId(999L);
    }
}
