package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.common.exception.TransferAlreadyCancelledException;
import com.bank.app.common.exception.TransferNotCancellableException;
import com.bank.app.common.exception.TransferNotFoundException;
import com.bank.app.common.security.port.SecurityContextPort;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.audit.application.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelTransferUseCaseTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private AccountOperationsPort accountOperationsPort;
    @Mock private AuditService auditService;
    @Mock private SecurityContextPort securityContextPort;

    private CancelTransferUseCase cancelTransferUseCase;

    @BeforeEach
    void setUp() {
        cancelTransferUseCase = new CancelTransferUseCase(loadTransferPort, saveTransferPort, accountOperationsPort, auditService, securityContextPort, 24);
    }

    @Test
    void shouldCancelTransferSuccessfully() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(new AccountOperationsPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        cancelTransferUseCase.execute(10L);

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());

        verify(accountOperationsPort).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());
        verify(saveTransferPort).save(transfer);
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.empty());

        TransferNotFoundException ex = assertThrows(TransferNotFoundException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Transfer bulunamadı. ID: 10", ex.getMessage());

        verifyNoInteractions(accountOperationsPort);
        verifyNoInteractions(saveTransferPort);
        verifyNoInteractions(securityContextPort);
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotAuthorizedToCancel() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(new AccountOperationsPort.AccountInfo(1L, 100L, "TRY", true));
        doThrow(new AccessDeniedException("Bu transferi iptal etmeye yetkiniz yok."))
                .when(securityContextPort).checkUserAuthorization(eq(100L), any());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Bu transferi iptal etmeye yetkiniz yok.", ex.getMessage());

        verify(accountOperationsPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
    }

    @Test
    void shouldPropagateAccessDeniedExceptionFromAccountOperationsPort() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(new AccountOperationsPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());
        doThrow(new AccessDeniedException("Bu transferi iptal etmeye yetkiniz yok."))
                .when(accountOperationsPort).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Bu transferi iptal etmeye yetkiniz yok.", ex.getMessage());

        verify(saveTransferPort, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenSenderAndReceiverAreSameAccount() {
        Transfer transfer = new Transfer(10L, 1L, 1L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Gönderici ve alıcı hesap aynı olamaz.", ex.getMessage());

        verifyNoInteractions(accountOperationsPort);
        verifyNoInteractions(securityContextPort);
    }

    @Test
    void shouldThrowTransferAlreadyCancelledExceptionWhenTransferIsCancelled() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.CANCELLED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(new AccountOperationsPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        TransferAlreadyCancelledException ex = assertThrows(TransferAlreadyCancelledException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertEquals("Transfer zaten iptal edilmiş. ID: 10", ex.getMessage());

        verify(accountOperationsPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenStatusIsPending() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.PENDING, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(new AccountOperationsPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertTrue(ex.getMessage().contains("Sadece tamamlanmış transferler iptal edilebilir"));

        verify(accountOperationsPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowTransferNotCancellableExceptionWhenCancellationWindowExpired() {
        Transfer transfer = new Transfer(10L, 1L, 2L, Money.of("200.00", Money.Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now().minusHours(25));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationsPort.getAccountInfo(1L)).thenReturn(new AccountOperationsPort.AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        TransferNotCancellableException ex = assertThrows(TransferNotCancellableException.class,
                () -> cancelTransferUseCase.execute(10L));
        assertTrue(ex.getMessage().contains("saat geçtiği için iptal edilemez"));

        verify(accountOperationsPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenTransferIdIsNull() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> cancelTransferUseCase.execute(null));
        assertEquals("Transfer ID null olamaz", ex.getMessage());
    }

    @Test
    void shouldHaveRetryableAnnotationOnExecuteMethod() throws Exception {
        Method method = CancelTransferUseCase.class.getMethod("execute", Long.class);
        Retryable retryable = method.getAnnotation(Retryable.class);
        assertNotNull(retryable, "execute method should be annotated with @Retryable");
    }

    @Test
    void shouldHaveTransactionalAnnotationOnClass() {
        Transactional transactional = CancelTransferUseCase.class.getAnnotation(Transactional.class);
        assertNotNull(transactional, "CancelTransferUseCase should be annotated with @Transactional");
    }
}
