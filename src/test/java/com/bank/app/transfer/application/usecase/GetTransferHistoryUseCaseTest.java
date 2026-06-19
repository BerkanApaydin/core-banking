package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.adapter.SecurityContextAdapter;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTransferHistoryUseCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountOperationPort accountOperationPort;
    private SecurityContextPort securityContextPort;
    private GetTransferHistoryUseCase getTransferHistoryUseCase;

    @BeforeEach
    void setUp() {
        securityContextPort = new SecurityContextAdapter();
        getTransferHistoryUseCase = new GetTransferHistoryUseCase(loadTransferPort, accountOperationPort,
                securityContextPort);

        // Set default authenticated user context using CustomUserDetails
        CustomUserDetails principal = new CustomUserDetails(100L, "test_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnTransferHistorySuccessfully() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);
        Transfer t1 = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED,
                LocalDateTime.now());

        when(accountOperationPort.getAccountInfo(1L)).thenReturn(account);
        when(accountOperationPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR290006200000000000000111",
                2L, "TR290006200000000000000222"));
        when(loadTransferPort.findHistory(eq(1L), anyInt(), anyInt())).thenReturn(Arrays.asList(t1));
        when(loadTransferPort.countHistory(1L)).thenReturn(1L);

        PagedResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L);

        assertNotNull(history);
        assertEquals(1, history.items().size());
        assertEquals(10L, history.items().get(0).id());
        assertEquals("TR290006200000000000000111", history.items().get(0).senderIban());
        assertEquals("TR290006200000000000000222", history.items().get(0).receiverIban());
        assertEquals(1, history.totalItems());
        assertEquals(0, history.page());
        assertEquals(20, history.size());
        assertEquals(1, history.totalPages());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOfAccount() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);

        // Set up authentication for user ID 999 (not owner of account 100)
        CustomUserDetails principal = new CustomUserDetails(999L, "other_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(accountOperationPort.getAccountInfo(1L)).thenReturn(account);

        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> getTransferHistoryUseCase.execute(1L));
        assertEquals("Bu hesabın işlem geçmişini görme yetkiniz yok.", exception.getMessage());
    }

    @Test
    void shouldCapPageSizeAtMaxLimit() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);

        when(accountOperationPort.getAccountInfo(1L)).thenReturn(account);
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
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);

        when(accountOperationPort.getAccountInfo(1L)).thenReturn(account);
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
        assertEquals("Account ID null olamaz", exception.getMessage());
    }
}
