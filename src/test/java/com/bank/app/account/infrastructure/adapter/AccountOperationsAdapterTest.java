package com.bank.app.account.infrastructure.adapter;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountOperationsAdapterTest {

    @Mock
    private AccountInternalService accountInternalService;

    @InjectMocks
    private AccountOperationsAdapter adapter;

    @Test
    void shouldMapAccountInfoCorrectly() {
        AccountInternalService.AccountInfo internalInfo =
                new AccountInternalService.AccountInfo(1L, 100L, "TRY", true);
        when(accountInternalService.getAccountInfo(1L)).thenReturn(internalInfo);

        var result = adapter.getAccountInfo(1L);

        assertEquals(1L, result.id());
        assertEquals(100L, result.userId());
        assertEquals("TRY", result.currency());
        assertTrue(result.active());
        verify(accountInternalService).getAccountInfo(1L);
    }

    @Test
    void shouldMapAccountInfoForTransferCorrectly() {
        AccountInternalService.AccountInfo internalInfo =
                new AccountInternalService.AccountInfo(2L, 200L, "USD", false);
        when(accountInternalService.getAccountInfoForTransfer("TR123")).thenReturn(internalInfo);

        var result = adapter.getAccountInfoForTransfer("TR123");

        assertEquals(2L, result.id());
        assertEquals(200L, result.userId());
        assertEquals("USD", result.currency());
        assertFalse(result.active());
        verify(accountInternalService).getAccountInfoForTransfer("TR123");
    }

    @Test
    void shouldDelegateDebitAndCredit() {
        Money amount = Money.of("100.00", Money.Currency.TRY);

        adapter.debitAndCredit(1L, 2L, amount);

        verify(accountInternalService).debitAndCredit(1L, 2L, amount);
    }

    @Test
    void shouldDelegateReverseBalancesForCancellation() {
        Money amount = Money.of("50.00", Money.Currency.TRY);

        adapter.reverseBalancesForCancellation(10L, 20L, amount);

        verify(accountInternalService).reverseBalancesForCancellation(10L, 20L, amount);
    }

    @Test
    void shouldMapEmptyIbansForAccounts() {
        when(accountInternalService.getIbansForAccounts(List.of())).thenReturn(Map.of());

        var result = adapter.getIbansForAccounts(List.of());

        assertTrue(result.isEmpty());
        verify(accountInternalService).getIbansForAccounts(List.of());
    }

    @Test
    void shouldMapIbansForAccounts() {
        when(accountInternalService.getIbansForAccounts(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, "TR1", 2L, "TR2"));

        var result = adapter.getIbansForAccounts(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("TR1", result.get(1L));
        assertEquals("TR2", result.get(2L));
    }
}
