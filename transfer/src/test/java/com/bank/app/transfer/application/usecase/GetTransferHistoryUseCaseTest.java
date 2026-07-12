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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class GetTransferHistoryUseCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountAclPort accountOperationPort;
    @Mock private TransferAuthorizationService transferAuthorizationService;
    private GetTransferHistoryQuery getTransferHistoryUseCase;

    @BeforeEach
    void setUp() {
        getTransferHistoryUseCase = new GetTransferHistoryUseCaseImpl(loadTransferPort, accountOperationPort,
                transferAuthorizationService);
    }

    @Test
    void shouldReturnTransferHistorySuccessfully() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
        Transfer t1 = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED,
                LocalDateTime.now());

        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(eq(Set.of(1L, 2L)))).thenReturn(Map.of(
                1L, "TR290006200000000000000111",
                2L, "TR290006200000000000000222"));
        when(loadTransferPort.findHistory(eq(1L), anyInt(), anyInt())).thenReturn(Arrays.asList(t1));
        when(loadTransferPort.countHistory(1L)).thenReturn(1L);

        PageResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L);

        assertNotNull(history);
        assertEquals(1, history.content().size());
        assertEquals(10L, history.content().get(0).id());
        assertEquals("TR290006200000000000000111", history.content().get(0).senderIban());
        assertEquals("TR290006200000000000000222", history.content().get(0).receiverIban());
        assertEquals(1, history.totalElements());
        assertEquals(0, history.page());
        assertEquals(20, history.size());
        assertEquals(1, history.totalPages());
        verify(accountOperationPort).getIbansForAccounts(Set.of(1L, 2L));
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOfAccount() {
        doThrow(new AccessDeniedException("You are not authorized to view this account's transaction history."))
                .when(transferAuthorizationService).authorizeAccountAccess(eq(1L), anyString());

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> getTransferHistoryUseCase.execute(1L));
        assertEquals("You are not authorized to view this account's transaction history.", exception.getMessage());
    }

    @Test
    void shouldCapPageSizeAtMaxLimit() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR290006200000000000000111",
                2L, "TR290006200000000000000222"));
        when(loadTransferPort.findHistory(eq(1L), eq(0), eq(100))).thenReturn(Collections.emptyList());
        when(loadTransferPort.countHistory(1L)).thenReturn(0L);

        getTransferHistoryUseCase.execute(1L, 0, Integer.MAX_VALUE);

        verify(loadTransferPort).findHistory(1L, 0, 100);
    }

    @Test
    void shouldCapNegativePageToZero() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");

        when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR290006200000000000000111",
                2L, "TR290006200000000000000222"));
        when(loadTransferPort.findHistory(eq(1L), eq(0), eq(20))).thenReturn(Collections.emptyList());
        when(loadTransferPort.countHistory(1L)).thenReturn(0L);

        getTransferHistoryUseCase.execute(1L, -5, 20);

        verify(loadTransferPort).findHistory(1L, 0, 20);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> getTransferHistoryUseCase.execute(null));
        assertEquals("Account ID must not be null", exception.getMessage());
    }
}
