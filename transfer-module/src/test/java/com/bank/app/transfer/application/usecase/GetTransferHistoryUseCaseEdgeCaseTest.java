package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.domain.exception.AuthorizationException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GetTransferHistoryUseCaseEdgeCaseTest {

        @Mock
        private LoadTransferPort loadTransferPort;
        @Mock
        private AccountAclPort accountAclPort;
        @Mock
        private TransferAuthorizationService transferAuthorizationService;
        private GetTransferHistoryQuery getTransferHistoryUseCase;

        @BeforeEach
        void setUp() {
                getTransferHistoryUseCase = new GetTransferHistoryUseCaseImpl(
                                loadTransferPort, accountAclPort, transferAuthorizationService);
        }

        @Test
        void shouldReturnEmptyListWhenNoTransfersFound() {
                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
                when(loadTransferPort.findHistory(eq(1L), anyInt(), anyInt()))
                                .thenReturn(Collections.emptyList());
                when(loadTransferPort.countHistory(1L)).thenReturn(0L);

                PagedResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L);

                assertNotNull(history);
                assertTrue(history.items().isEmpty());
        }

        @Test
        void shouldReturnEmptyListWithPaginationWhenNoTransfersFound() {
                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
                when(loadTransferPort.findHistory(eq(1L), eq(0), eq(10)))
                                .thenReturn(Collections.emptyList());
                when(loadTransferPort.countHistory(1L)).thenReturn(0L);

                PagedResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L, 0, 10);

                assertNotNull(history);
                assertTrue(history.items().isEmpty());
        }

        @Test
        void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
                when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString()))
                                .thenThrow(new AccountNotFoundException(1L));

                assertThrows(AccountNotFoundException.class,
                                () -> getTransferHistoryUseCase.execute(1L));
                verifyNoInteractions(loadTransferPort);
        }

        @Test
        void shouldThrowAccessDeniedExceptionWhenUserIsNotOwner() {
                doThrow(new AuthorizationException("You are not authorized to view this account's transaction history."))
                        .when(transferAuthorizationService).authorizeAccountAccess(eq(1L), anyString());

                AuthorizationException ex = assertThrows(AuthorizationException.class,
                                () -> getTransferHistoryUseCase.execute(1L));
                assertEquals("You are not authorized to view this account's transaction history.", ex.getMessage());
                verifyNoInteractions(loadTransferPort);
        }

        @Test
        void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
                NullPointerException ex = assertThrows(NullPointerException.class,
                                () -> getTransferHistoryUseCase.execute(null));
                assertEquals("Account ID must not be null", ex.getMessage());
        }

        @Test
        void shouldThrowNullPointerExceptionWhenAccountIdIsNullWithPagination() {
                assertThrows(NullPointerException.class,
                                () -> getTransferHistoryUseCase.execute(null, 0, 20));
        }

        @Test
        void shouldCallFindHistoryWithCorrectPagination() {
                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
                when(loadTransferPort.findHistory(eq(1L), eq(0), eq(20)))
                                .thenReturn(Collections.emptyList());
                when(accountAclPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
                when(loadTransferPort.countHistory(1L)).thenReturn(0L);

                getTransferHistoryUseCase.execute(1L, 0, 20);

                verify(loadTransferPort).findHistory(1L, 0, 20);
        }

        @Test
        void shouldDefaultToPage0AndSize20() {
                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(transferAuthorizationService.authorizeAccountAccess(eq(1L), anyString())).thenReturn(account);
                when(loadTransferPort.findHistory(eq(1L), eq(0), eq(20)))
                                .thenReturn(Collections.emptyList());
                when(accountAclPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
                when(loadTransferPort.countHistory(1L)).thenReturn(0L);

                getTransferHistoryUseCase.execute(1L);

                verify(loadTransferPort).findHistory(1L, 0, 20);
        }

        @Test
        void shouldThrowAccessDeniedExceptionWhenNotLoggedIn() {
                doThrow(new AuthorizationException("Session not found."))
                        .when(transferAuthorizationService).authorizeAccountAccess(eq(1L), anyString());

                assertThrows(AuthorizationException.class,
                                () -> getTransferHistoryUseCase.execute(1L));
        }
}
