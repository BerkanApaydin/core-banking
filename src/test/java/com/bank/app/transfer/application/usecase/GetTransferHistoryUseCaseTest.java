package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.security.SecurityUtils;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetTransferHistoryUseCaseTest {

    private LoadTransferPort loadTransferPort;
    private AccountOperationsPort accountOperationsPort;
    private SecurityContextPort securityContextPort;
    private GetTransferHistoryUseCase getTransferHistoryUseCase;

    @BeforeEach
    void setUp() {
        loadTransferPort = mock(LoadTransferPort.class);
        accountOperationsPort = mock(AccountOperationsPort.class);
        securityContextPort = new SecurityUtils();
        getTransferHistoryUseCase = new GetTransferHistoryUseCase(loadTransferPort, accountOperationsPort,
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

        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);
        when(accountOperationsPort.getIbansForAccounts(anySet())).thenReturn(Map.of(
                1L, "TR290006200000000000000111",
                2L, "TR290006200000000000000222"));
        when(loadTransferPort.findHistory(eq(1L), anyInt(), anyInt())).thenReturn(Arrays.asList(t1));

        List<TransferResponse> history = getTransferHistoryUseCase.execute(1L);

        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals(10L, history.get(0).id());
        assertEquals("TR290006200000000000000111", history.get(0).senderIban());
        assertEquals("TR290006200000000000000222", history.get(0).receiverIban());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOfAccount() {
        AccountInfo account = new AccountInfo(1L, 100L, "TRY", true);

        // Set up authentication for user ID 999 (not owner of account 100)
        CustomUserDetails principal = new CustomUserDetails(999L, "other_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
                Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(account);

        assertThrows(AccessDeniedException.class, () -> getTransferHistoryUseCase.execute(1L));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAccountIdIsNull() {
        assertThrows(NullPointerException.class, () -> getTransferHistoryUseCase.execute(null));
    }
}
