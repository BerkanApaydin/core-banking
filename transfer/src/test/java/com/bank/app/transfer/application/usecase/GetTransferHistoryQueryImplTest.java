package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.common.application.dto.PageResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.service.TransferViewEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.domain.exception.AuthorizationException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import com.bank.app.common.domain.AccountId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GetTransferHistoryQueryImplTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountAclPort accountOperationPort;
    @Mock private TransferAuthorizationService transferAuthorizationService;
    private GetTransferHistoryQuery getTransferHistoryUseCase;

    @BeforeEach
    void setUp() {
        getTransferHistoryUseCase = new GetTransferHistoryQueryImpl(loadTransferPort,
                new TransferViewEnricher(accountOperationPort), transferAuthorizationService, 100);
    }

    @Test
    void shouldReturnTransferHistorySuccessfully() {
        AccountInfo account = new AccountInfo(new AccountId(1L), 100L, "TRY", "ACTIVE");
        Transfer t1 = new Transfer(10L, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
                LocalDateTime.now());

        when(transferAuthorizationService.authorizeAccountAccess(eq(new AccountId(1L)), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222"));
        when(loadTransferPort.findHistoryPage(eq(1L), anyInt(), anyInt()))
                .thenReturn(new LoadTransferPort.HistoryPage(Arrays.asList(t1), 1L));

        PageResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L, 0, 20);

        assertNotNull(history);
        assertEquals(1, history.content().size());
        assertEquals(10L, history.content().get(0).id());
        assertEquals("TR770006200000000000000111", history.content().get(0).senderIban());
        assertEquals("TR870006200000000000000222", history.content().get(0).receiverIban());
        assertEquals(1, history.totalElements());
        assertEquals(0, history.page());
        assertEquals(20, history.size());
        assertEquals(1, history.totalPages());
        verify(accountOperationPort).getIbansForAccounts(Set.of(1L, 2L));
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOfAccount() {
        doThrow(new AuthorizationException("You are not authorized to view this account's transaction history."))
                .when(transferAuthorizationService).authorizeAccountAccess(eq(new AccountId(1L)), anyString());

        AuthorizationException exception = assertThrows(AuthorizationException.class, () -> getTransferHistoryUseCase.execute(1L, 0, 20));
        assertEquals("You are not authorized to view this account's transaction history.", exception.getMessage());
    }

    @Test
    void shouldCapPageSizeAtMaxLimit() {
        AccountInfo account = new AccountInfo(new AccountId(1L), 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(new AccountId(1L)), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222"));
        when(loadTransferPort.findHistoryPage(eq(1L), eq(0), eq(100)))
                .thenReturn(new LoadTransferPort.HistoryPage(Collections.emptyList(), 0L));

        getTransferHistoryUseCase.execute(1L, 0, Integer.MAX_VALUE);

        verify(loadTransferPort).findHistoryPage(1L, 0, 100);
    }

    @Test
    void shouldCapNegativePageToZero() {
        AccountInfo account = new AccountInfo(new AccountId(1L), 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(new AccountId(1L)), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222"));
        when(loadTransferPort.findHistoryPage(eq(1L), eq(0), eq(20)))
                .thenReturn(new LoadTransferPort.HistoryPage(Collections.emptyList(), 0L));

        getTransferHistoryUseCase.execute(1L, -5, 20);

        verify(loadTransferPort).findHistoryPage(1L, 0, 20);
    }

    @Test
    void shouldServePageAndTotalFromASinglePortCall() {
        AccountInfo account = new AccountInfo(new AccountId(1L), 100L, "TRY", "ACTIVE");
        Transfer t1 = new Transfer(10L, new AccountId(1L), new AccountId(2L), Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
                LocalDateTime.now());
        Transfer t2 = new Transfer(11L, new AccountId(1L), new AccountId(2L), Money.of("50.00", Currency.TRY), TransferStatus.COMPLETED,
                LocalDateTime.now());

        when(transferAuthorizationService.authorizeAccountAccess(eq(new AccountId(1L)), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR770006200000000000000111",
                2L, "TR870006200000000000000222"));
        // One port call carries both rows and the exact total: no second COUNT query.
        when(loadTransferPort.findHistoryPage(eq(1L), eq(0), eq(2)))
                .thenReturn(new LoadTransferPort.HistoryPage(Arrays.asList(t1, t2), 5L));

        PageResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L, 0, 2);

        assertEquals(2, history.content().size());
        assertEquals(5, history.totalElements());
        assertEquals(3, history.totalPages());
        assertFalse(history.last());
        verify(loadTransferPort, times(1)).findHistoryPage(1L, 0, 2);
        verifyNoMoreInteractions(loadTransferPort);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> getTransferHistoryUseCase.execute(null, 0, 20));
        assertEquals("Account ID must not be null", exception.getMessage());
    }

    @Test
    void shouldFallBackToDefaultPageSizeWhenMisconfigured() {
        GetTransferHistoryQuery misconfigured = new GetTransferHistoryQueryImpl(loadTransferPort,
                new TransferViewEnricher(accountOperationPort), transferAuthorizationService, 0);
        AccountInfo account = new AccountInfo(new AccountId(1L), 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(new AccountId(1L)), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of());
        when(loadTransferPort.findHistoryPage(eq(1L), eq(0), eq(100)))
                .thenReturn(new LoadTransferPort.HistoryPage(Collections.emptyList(), 0L));

        misconfigured.execute(1L, 0, Integer.MAX_VALUE);

        verify(loadTransferPort).findHistoryPage(1L, 0, 100);
    }

    @Test
    void shouldRejectDeepOffsetWindow() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> getTransferHistoryUseCase.execute(1L, 200, 100));

        assertTrue(exception.getMessage().contains("keyset"));
        verifyNoInteractions(loadTransferPort);
    }

    @Test
    void shouldAcceptBoundaryOffsetWindow() {
        AccountInfo account = new AccountInfo(new AccountId(1L), 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(new AccountId(1L)), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of());
        when(loadTransferPort.findHistoryPage(eq(1L), eq(100), eq(100)))
                .thenReturn(new LoadTransferPort.HistoryPage(Collections.emptyList(), 0L));

        getTransferHistoryUseCase.execute(1L, 100, 100);

        verify(loadTransferPort).findHistoryPage(1L, 100, 100);
    }
}
