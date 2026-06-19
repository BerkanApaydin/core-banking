package com.bank.app.transfer.application.usecase;

import com.bank.app.account.exception.AccountNotFoundException;
import com.bank.app.common.adapter.SecurityContextAdapter;
import com.bank.app.common.security.CustomUserDetails;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.port.LoadTransferPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTransferHistoryUseCaseEdgeCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private AccountOperationsPort accountOperationsPort;

    private SecurityContextPort securityContextPort;
    private GetTransferHistoryUseCase getTransferHistoryUseCase;

    @BeforeEach
    void setUp() {
        securityContextPort = new SecurityContextAdapter();
        getTransferHistoryUseCase = new GetTransferHistoryUseCase(
                loadTransferPort, accountOperationsPort, securityContextPort);

        CustomUserDetails principal = new CustomUserDetails(100L, "test_user",
                "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnEmptyListWhenNoTransfersFound() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);
        when(loadTransferPort.findHistory(eq(1L), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(loadTransferPort.countHistory(1L)).thenReturn(0L);

        PagedResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L);

        assertNotNull(history);
        assertTrue(history.items().isEmpty());
    }

    @Test
    void shouldReturnEmptyListWithPaginationWhenNoTransfersFound() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);
        when(loadTransferPort.findHistory(eq(1L), eq(0), eq(10)))
                .thenReturn(Collections.emptyList());
        when(loadTransferPort.countHistory(1L)).thenReturn(0L);

        PagedResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L, 0, 10);

        assertNotNull(history);
        assertTrue(history.items().isEmpty());
    }

    @Test
    void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
        when(accountOperationsPort.getAccountInfo(1L))
                .thenThrow(new AccountNotFoundException(1L));

        assertThrows(AccountNotFoundException.class,
                () -> getTransferHistoryUseCase.execute(1L));
        verifyNoInteractions(loadTransferPort);
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwner() {
        AccountInfo account = new AccountInfo(1L, 200L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> getTransferHistoryUseCase.execute(1L));
        assertEquals("Bu hesabın işlem geçmişini görme yetkiniz yok.", ex.getMessage());
        verifyNoInteractions(loadTransferPort);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> getTransferHistoryUseCase.execute(null));
        assertEquals("Account ID null olamaz", ex.getMessage());
        verifyNoInteractions(accountOperationsPort);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNullWithPagination() {
        assertThrows(NullPointerException.class,
                () -> getTransferHistoryUseCase.execute(null, 0, 20));
    }

    @Test
    void shouldCallFindHistoryWithCorrectPagination() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);
        when(loadTransferPort.findHistory(eq(1L), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.countHistory(1L)).thenReturn(0L);

        getTransferHistoryUseCase.execute(1L, 0, 20);

        verify(loadTransferPort).findHistory(1L, 0, 20);
    }

    @Test
    void shouldDefaultToPage0AndSize20() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);
        when(loadTransferPort.findHistory(eq(1L), eq(0), eq(20)))
                .thenReturn(Collections.emptyList());
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
        when(loadTransferPort.countHistory(1L)).thenReturn(0L);

        getTransferHistoryUseCase.execute(1L);

        verify(loadTransferPort).findHistory(1L, 0, 20);
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotLoggedIn() {
        SecurityContextHolder.clearContext();

        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);

        assertThrows(AccessDeniedException.class,
                () -> getTransferHistoryUseCase.execute(1L));
    }
}
