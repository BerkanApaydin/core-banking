package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelTransferUseCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private AccountOperationPort accountOperationPort;
    @Mock private EventPublisherPort eventPublisherPort;
    @Mock private SecurityContextPort securityContextPort;

    private CancelTransferUseCase cancelTransferUseCase;

    @BeforeEach
    void setUp() {
        cancelTransferUseCase = new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort, accountOperationPort, eventPublisherPort, securityContextPort, 24);
    }

    @Test
    void shouldCancelTransferSuccessfully() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L)).thenReturn(new AccountOperationPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        cancelTransferUseCase.execute(10L);

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());

        verify(accountOperationPort).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());
        verify(saveTransferPort).save(transfer);
        verify(eventPublisherPort).publish(any(TransferCancelledEvent.class));
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.empty());

        TransferNotFoundException ex = assertThrows(TransferNotFoundException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Transfer bulunamadı. ID: 10", ex.getMessage());

        verifyNoInteractions(accountOperationPort);
        verifyNoInteractions(saveTransferPort);
        verifyNoInteractions(securityContextPort);
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotAuthorizedToCancel() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L)).thenReturn(new AccountOperationPort.AccountInfo(1L, 100L, "TRY", true));
        doThrow(new AccessDeniedException("Bu transferi iptal etmeye yetkiniz yok."))
                .when(securityContextPort).checkUserAuthorization(eq(100L), any());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Bu transferi iptal etmeye yetkiniz yok.", ex.getMessage());

        verify(accountOperationPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
    }

    @Test
    void shouldPropagateAccessDeniedExceptionFromAccountOperationsPort() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L)).thenReturn(new AccountOperationPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());
        doThrow(new AccessDeniedException("Bu transferi iptal etmeye yetkiniz yok."))
                .when(accountOperationPort).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Bu transferi iptal etmeye yetkiniz yok.", ex.getMessage());

        verify(saveTransferPort, never()).save(any());
    }

    @Test
    void shouldThrowTransferAlreadyCancelledExceptionWhenTransferIsCancelled() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L)).thenReturn(new AccountOperationPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        TransferAlreadyCancelledException ex = assertThrows(TransferAlreadyCancelledException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Transfer zaten iptal edilmiş. ID: 10", ex.getMessage());

        verify(accountOperationPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenStatusIsPending() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.PENDING, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L)).thenReturn(new AccountOperationPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertTrue(ex.getMessage().contains("Sadece tamamlanmış transferler iptal edilebilir"));

        verify(accountOperationPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenCancellationWindowExpired() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(25));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L)).thenReturn(new AccountOperationPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));

        verify(accountOperationPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> cancelTransferUseCase.execute(null));
        assertEquals("Transfer ID null olamaz", ex.getMessage());
    }

}
