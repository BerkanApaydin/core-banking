package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.common.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.audit.application.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CancelTransferUseCaseTest {

    private LoadTransferPort loadTransferPort;
    private SaveTransferPort saveTransferPort;
    private AccountInternalService accountInternalService;
    private AuditService auditService;
    private CancelTransferUseCase cancelTransferUseCase;

    @BeforeEach
    void setUp() {
        loadTransferPort = mock(LoadTransferPort.class);
        saveTransferPort = mock(SaveTransferPort.class);
        accountInternalService = mock(AccountInternalService.class);
        auditService = mock(AuditService.class);
        cancelTransferUseCase = new CancelTransferUseCase(loadTransferPort, saveTransferPort, accountInternalService, auditService, 24);
    }

    @Test
    void shouldCancelTransferSuccessfully() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));

        cancelTransferUseCase.execute(10L);

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());

        // Verify balance reversal was delegated to AccountInternalService
        verify(accountInternalService).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());
        verify(saveTransferPort).save(transfer);
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.empty());

        assertThrows(TransferNotFoundException.class, () -> cancelTransferUseCase.execute(10L));

        verifyNoInteractions(accountInternalService);
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldPropagateAccessDeniedExceptionFromAccountInternalService() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        doThrow(new AccessDeniedException("Bu transferi iptal etmeye yetkiniz yok."))
                .when(accountInternalService).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());

        assertThrows(AccessDeniedException.class, () -> cancelTransferUseCase.execute(10L));

        verify(saveTransferPort, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenSenderAndReceiverAreSameAccount() {
        Transfer transfer = new Transfer(10L, 1L, 1L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));

        assertThrows(IllegalArgumentException.class, () -> cancelTransferUseCase.execute(10L));
        verifyNoInteractions(accountInternalService);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        assertThrows(NullPointerException.class, () -> cancelTransferUseCase.execute(null));
    }
}
