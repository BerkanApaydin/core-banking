package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
    void shouldFindByIbanForUpdateSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000111");
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, iban.value(), "Ahmet", new BigDecimal("1000.00"),
                "TRY", "ACTIVE", null);

        when(springDataRepo.findByIbanForUpdate(iban.value())).thenReturn(Optional.of(jpaEntity));

        var result = adapter.findByIbanForUpdate(iban);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        assertEquals(new BigDecimal("1000.00"), result.get().getBalance().amount());
        verify(springDataRepo).findByIbanForUpdate(iban.value());
    }

    @Test
    void shouldReturnEmptyWhenFindByIbanForUpdateNotFound() {
        Iban iban = new Iban("TR290006200000000000000999");

        when(springDataRepo.findByIbanForUpdate(iban.value())).thenReturn(Optional.empty());

        var result = adapter.findByIbanForUpdate(iban);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIbanForUpdate(iban.value());
    }

    @Test
    void shouldFindByIdForUpdateSuccessfully() {
        AccountJpaEntity jpaEntity = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet",
                new BigDecimal("1000.00"), "TRY", "ACTIVE", null);

        when(springDataRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(jpaEntity));

        var result = adapter.findByIdForUpdate(1L);

        assertTrue(result.isPresent());
        assertEquals("Ahmet", result.get().getOwnerName());
        verify(springDataRepo).findByIdForUpdate(1L);
    }

    @Test
    void shouldReturnEmptyWhenFindByIdForUpdateNotFound() {
        when(springDataRepo.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        var result = adapter.findByIdForUpdate(999L);

        assertFalse(result.isPresent());
        verify(springDataRepo).findByIdForUpdate(999L);
    }

    @Test
    void shouldFindByUserIdSuccessfully() {
        AccountJpaEntity entity1 = createEntity(1L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"),
                100L);
        AccountJpaEntity entity2 = createEntity(2L, "TR290006200000000000000222", "Mehmet", new BigDecimal("500.00"),
                100L);

        when(springDataRepo.findByUserIdOrderByCreatedAtDesc(100L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(entity1, entity2)));

        var result = adapter.findByUserId(100L, 0, 20);

        assertEquals(2, result.size());
        assertEquals("Ahmet", result.get(0).getOwnerName());
        assertEquals("Mehmet", result.get(1).getOwnerName());
        verify(springDataRepo).findByUserIdOrderByCreatedAtDesc(100L, PageRequest.of(0, 20));
    }

    @Test
    void shouldReturnEmptyPageWhenFindByUserIdReturnsEmpty() {
        when(springDataRepo.findByUserIdOrderByCreatedAtDesc(999L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = adapter.findByUserId(999L, 0, 20);

        assertTrue(result.isEmpty());
        verify(springDataRepo).findByUserIdOrderByCreatedAtDesc(999L, PageRequest.of(0, 20));
    }

    @Test
    void shouldCountByUserId() {
        when(springDataRepo.countByUserId(100L)).thenReturn(2L);

        assertEquals(2L, adapter.countByUserId(100L));
        verify(springDataRepo).countByUserId(100L);
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
}
