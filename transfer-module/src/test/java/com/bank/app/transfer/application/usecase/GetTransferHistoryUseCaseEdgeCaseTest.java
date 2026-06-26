package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.common.adapter.out.security.SecurityContextAdapter;
import com.bank.app.transfer.application.port.in.GetTransferHistoryQuery;
import com.bank.app.common.adapter.out.security.CustomUserDetails;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.transfer.application.dto.PagedResponse;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.domain.exception.AuthorizationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GetTransferHistoryUseCaseEdgeCaseTest {

        @Mock
        private LoadTransferPort loadTransferPort;
        @Mock
        private AccountAclPort accountAclPort;

        private SecurityContextPort securityContextPort;
        private GetTransferHistoryQuery getTransferHistoryUseCase;

        @BeforeEach
        void setUp() {
                securityContextPort = new SecurityContextAdapter();
                getTransferHistoryUseCase = new GetTransferHistoryUseCaseImpl(
                                loadTransferPort, accountAclPort, securityContextPort);

                CustomUserDetails principal = new CustomUserDetails(100L, "test_user",
                                "password", Collections.emptyList());
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null,
                                Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
        }

        @AfterEach
        void tearDown() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void shouldReturnEmptyListWhenNoTransfersFound() {
                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(accountAclPort.getAccountInfo(1L)).thenReturn(account);
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
                when(accountAclPort.getAccountInfo(1L)).thenReturn(account);
                when(loadTransferPort.findHistory(eq(1L), eq(0), eq(10)))
                                .thenReturn(Collections.emptyList());
                when(loadTransferPort.countHistory(1L)).thenReturn(0L);

                PagedResponse<TransferResponse> history = getTransferHistoryUseCase.execute(1L, 0, 10);

                assertNotNull(history);
                assertTrue(history.items().isEmpty());
        }

        @Test
        void shouldThrowAccountNotFoundExceptionWhenAccountDoesNotExist() {
                when(accountAclPort.getAccountInfo(1L))
                                .thenThrow(new AccountNotFoundException(1L));

                assertThrows(AccountNotFoundException.class,
                                () -> getTransferHistoryUseCase.execute(1L));
                verifyNoInteractions(loadTransferPort);
        }

        @Test
        void shouldThrowAccessDeniedExceptionWhenUserIsNotOwner() {
                AccountInfo account = new AccountInfo(1L, 200L, "TRY", "ACTIVE");
                when(accountAclPort.getAccountInfo(1L)).thenReturn(account);

                AuthorizationException ex = assertThrows(AuthorizationException.class,
                                () -> getTransferHistoryUseCase.execute(1L));
                assertEquals("Bu hesabın işlem geçmişini görme yetkiniz yok.", ex.getMessage());
                verifyNoInteractions(loadTransferPort);
        }

        @Test
        void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
                NullPointerException ex = assertThrows(NullPointerException.class,
                                () -> getTransferHistoryUseCase.execute(null));
                assertEquals("Account ID null olamaz", ex.getMessage());
                verifyNoInteractions(accountAclPort);
        }

        @Test
        void shouldThrowNullPointerExceptionWhenAccountIdIsNullWithPagination() {
                assertThrows(NullPointerException.class,
                                () -> getTransferHistoryUseCase.execute(null, 0, 20));
        }

        @Test
        void shouldCallFindHistoryWithCorrectPagination() {
                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(accountAclPort.getAccountInfo(1L)).thenReturn(account);
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
                when(accountAclPort.getAccountInfo(1L)).thenReturn(account);
                when(loadTransferPort.findHistory(eq(1L), eq(0), eq(20)))
                                .thenReturn(Collections.emptyList());
                when(accountAclPort.getIbansForAccounts(anySet())).thenReturn(Collections.emptyMap());
                when(loadTransferPort.countHistory(1L)).thenReturn(0L);

                getTransferHistoryUseCase.execute(1L);

                verify(loadTransferPort).findHistory(1L, 0, 20);
        }

        @Test
        void shouldThrowAccessDeniedExceptionWhenNotLoggedIn() {
                SecurityContextHolder.clearContext();

                AccountInfo account = new AccountInfo(1L, 100L, "TRY", "ACTIVE");
                when(accountAclPort.getAccountInfo(1L)).thenReturn(account);

                assertThrows(AuthorizationException.class,
                                () -> getTransferHistoryUseCase.execute(1L));
        }
}
