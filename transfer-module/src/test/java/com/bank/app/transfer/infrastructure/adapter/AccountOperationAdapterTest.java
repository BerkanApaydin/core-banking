package com.bank.app.transfer.infrastructure.adapter;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.in.AccountQueryPort;
import com.bank.app.account.application.port.in.AccountTransferOperationPort;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
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
class AccountOperationAdapterTest {

    @Mock
    private AccountQueryPort accountQueryPort;

    @Mock
    private AccountTransferOperationPort accountTransferOperation;

    @InjectMocks
    private AccountOperationAdapter adapter;

    @Test
    void shouldMapAccountInfoCorrectly() {
        AccountInfo internalInfo = new AccountInfo(1L, 100L, "TRY", AccountStatus.ACTIVE);
        when(accountQueryPort.getAccountInfo(1L)).thenReturn(internalInfo);

        var result = adapter.getAccountInfo(1L);

        assertEquals(1L, result.id());
        assertEquals(100L, result.userId());
        assertEquals("TRY", result.currency());
        assertTrue(result.active());
        verify(accountQueryPort).getAccountInfo(1L);
    }

    @Test
    void shouldMapAccountInfoForTransferCorrectly() {
        AccountInfo internalInfo = new AccountInfo(2L, 200L, "USD", AccountStatus.SUSPENDED);
        when(accountQueryPort.getAccountInfoForTransfer("TR123")).thenReturn(internalInfo);

        var result = adapter.getAccountInfoForTransfer("TR123");

        assertEquals(2L, result.id());
        assertEquals(200L, result.userId());
        assertEquals("USD", result.currency());
        assertFalse(result.active());
        verify(accountQueryPort).getAccountInfoForTransfer("TR123");
    }

    @Test
    void shouldDelegateDebitAndCredit() {
        Money amount = Money.of("100.00", Currency.TRY);

        adapter.debitAndCredit(1L, 2L, amount);

        verify(accountTransferOperation).executeTransfer(1L, 2L, amount);
    }

    @Test
    void shouldDelegateReverseBalancesForCancellation() {
        Money amount = Money.of("50.00", Currency.TRY);

        adapter.reverseBalancesForCancellation(10L, 20L, amount);

        verify(accountTransferOperation).reverseTransfer(10L, 20L, amount);
    }

    @Test
    void shouldMapEmptyIbansForAccounts() {
        when(accountQueryPort.getIbansForAccounts(List.of())).thenReturn(Map.of());

        var result = adapter.getIbansForAccounts(List.of());

        assertTrue(result.isEmpty());
        verify(accountQueryPort).getIbansForAccounts(List.of());
    }

    @Test
    void shouldMapIbansForAccounts() {
        when(accountQueryPort.getIbansForAccounts(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, "TR1", 2L, "TR2"));

        var result = adapter.getIbansForAccounts(List.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("TR1", result.get(1L));
        assertEquals("TR2", result.get(2L));
    }
}
