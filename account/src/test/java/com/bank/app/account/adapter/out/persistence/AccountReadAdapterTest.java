package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class AccountReadAdapterTest {

    @Mock
    private AccountJpaRepository springDataRepo;

    private final AccountJpaMapper mapper = new AccountJpaMapper();

    private AccountPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new AccountPersistenceAdapter(springDataRepo, mapper);
    }

    private AccountJpaEntity createEntity(Long id, String iban, String ownerName, BigDecimal balance, Long userId) {
        return new AccountJpaEntity(id, userId, iban, ownerName, balance, "TRY", "ACTIVE", null);
    }

    @Test
    void shouldFindByIbanSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = createEntity(1L, iban.value(), "Ahmet", new BigDecimal("1000.00"), 100L);

        when(springDataRepo.findByIban(iban.value())).thenReturn(Optional.of(jpaEntity));

        Optional<Account> result = adapter.findByIban(iban);

        assertTrue(result.isPresent());
        Account account = result.get();
        assertEquals("Ahmet", account.getOwnerName());
        assertEquals(1L, account.getId());
        assertEquals(100L, account.getUserId().value());
        assertEquals("TR290006200000000000000111", account.getIban().value());
        assertEquals(new BigDecimal("1000.00"), account.getBalance().amount());
        assertTrue(account.isActive());
        verify(springDataRepo).findByIban(iban.value());
    }

    @Test
    void shouldReturnEmptyWhenFindByIbanNotFound() {
        Iban iban = new Iban("TR290006200000000000000999");

        when(springDataRepo.findByIban(iban.value())).thenReturn(Optional.empty());

        Optional<Account> result = adapter.findByIban(iban);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIban(iban.value());
    }

    @Test
    void shouldFindByIdSuccessfully() {
        AccountJpaEntity jpaEntity = createEntity(1L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"),
                100L);

        when(springDataRepo.findById(1L)).thenReturn(Optional.of(jpaEntity));

        Optional<Account> result = adapter.findById(1L);

        assertTrue(result.isPresent());
        Account account = result.get();
        assertEquals(1L, account.getId());
        assertEquals("Ahmet", account.getOwnerName());
        assertEquals(100L, account.getUserId().value());
        assertEquals(new BigDecimal("1000.00"), account.getBalance().amount());
        verify(springDataRepo).findById(1L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdNotFound() {
        when(springDataRepo.findById(999L)).thenReturn(Optional.empty());

        Optional<Account> result = adapter.findById(999L);

        assertFalse(result.isPresent());
        verify(springDataRepo).findById(999L);
    }

    @Test
    void shouldFindByIbanWithLockSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"),
                "TRY", "ACTIVE", null);

        when(springDataRepo.findByIbanWithLock(iban.value())).thenReturn(Optional.of(jpaEntity));

        var result = adapter.findByIbanWithLock(iban);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        assertEquals(new BigDecimal("1000.00"), result.get().getBalance().amount());
        verify(springDataRepo).findByIbanWithLock(iban.value());
    }

    @Test
    void shouldReturnEmptyWhenFindByIbanWithLockNotFound() {
        Iban iban = new Iban("TR290006200000000000000999");

        when(springDataRepo.findByIbanWithLock(iban.value())).thenReturn(Optional.empty());

        var result = adapter.findByIbanWithLock(iban);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIbanWithLock(iban.value());
    }

    @Test
    void shouldFindByIdWithLockSuccessfully() {
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet",
                new BigDecimal("1000.00"), "TRY", "ACTIVE", null);

        when(springDataRepo.findByIdWithLock(1L)).thenReturn(Optional.of(jpaEntity));

        var result = adapter.findByIdWithLock(1L);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        verify(springDataRepo).findByIdWithLock(1L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdWithLockNotFound() {
        when(springDataRepo.findByIdWithLock(999L)).thenReturn(Optional.empty());

        var result = adapter.findByIdWithLock(999L);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIdWithLock(999L);
    }

    @Test
    void shouldFindAllSuccessfully() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet",
                new BigDecimal("1000.00"), "TRY", "ACTIVE", null);
        AccountJpaEntity entity2 = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222", "Mehmet",
                new BigDecimal("500.00"), "TRY", "ACTIVE", null);

        when(springDataRepo.findAll()).thenReturn(List.of(entity1, entity2));

        var result = adapter.findAll();

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldReturnEmptyListWhenFindAllReturnsEmpty() {
        when(springDataRepo.findAll()).thenReturn(List.of());

        var result = adapter.findAll();

        assertTrue(result.isEmpty());
        verify(springDataRepo).findAll();
    }

    @Test
    void shouldFindByUserIdSuccessfully() {
        AccountJpaEntity entity1 = createEntity(1L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"),
                100L);
        AccountJpaEntity entity2 = createEntity(2L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"),
                100L);

        when(springDataRepo.findByUserIdOrderByCreatedAtDesc(100L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(entity1, entity2)));

        var result = adapter.findByUserId(100L, Pageable.unpaged());

        assertEquals(2, result.getNumberOfElements());
        assertEquals("Ahmet", result.getContent().get(0).getOwnerName());
        assertEquals("Mehmet", result.getContent().get(1).getOwnerName());
        verify(springDataRepo).findByUserIdOrderByCreatedAtDesc(100L, Pageable.unpaged());
    }

    @Test
    void shouldReturnEmptyPageWhenFindByUserIdReturnsEmpty() {
        when(springDataRepo.findByUserIdOrderByCreatedAtDesc(999L, Pageable.unpaged()))
                .thenReturn(Page.empty());

        var result = adapter.findByUserId(999L, Pageable.unpaged());

        assertTrue(result.getContent().isEmpty());
        verify(springDataRepo).findByUserIdOrderByCreatedAtDesc(999L, Pageable.unpaged());
    }

    @Test
    void shouldFindByIdsSuccessfully() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet",
                new BigDecimal("1000.00"), "TRY", "ACTIVE", null);
        AccountJpaEntity entity2 = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222", "Mehmet",
                new BigDecimal("500.00"), "TRY", "ACTIVE", null);

        when(springDataRepo.findByIdIn(List.of(1L, 2L))).thenReturn(List.of(entity1, entity2));

        var result = adapter.findByIds(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findByIdIn(List.of(1L, 2L));
    }

    @Test
    void shouldReturnEmptyListWhenFindByIdsIsNull() {
        var result = adapter.findByIds(null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(springDataRepo);
    }

    @Test
    void shouldReturnEmptyListWhenFindByIdsIsEmpty() {
        var result = adapter.findByIds(List.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(springDataRepo);
    }

    @Test
    void shouldThrowWhenEntityHasInvalidCurrencyOnFindAll() {
        AccountJpaEntity entity1 = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet",
                new BigDecimal("1000.00"), "INVALID", "ACTIVE", null);

        when(springDataRepo.findAll()).thenReturn(List.of(entity1));

        assertThrows(IllegalArgumentException.class, () -> adapter.findAll());
        verify(springDataRepo).findAll();
    }
}
