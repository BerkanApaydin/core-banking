package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelTransferUseCaseDecoratorTest {

    @Mock private LoadTransferPort loadTransferPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private AccountOperationPort accountOperationPort;
    @Mock private EventPublisherPort eventPublisherPort;
    @Mock private SecurityContextPort securityContextPort;

    private CancelTransferUseCaseDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new CancelTransferUseCaseDecorator(
                loadTransferPort, saveTransferPort, accountOperationPort,
                eventPublisherPort, securityContextPort, 24);
    }

    @Test
    void shouldDelegateAndCancelTransfer() {
        Transfer transfer = new Transfer(10L, 1L, 2L,
                Money.of("200.00", Money.Currency.TRY),
                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));

        when(loadTransferPort.findByIdWithLock(10L)).thenReturn(Optional.of(transfer));
        when(accountOperationPort.getAccountInfo(1L))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), any());

        decorator.execute(10L);

        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
        verify(accountOperationPort).reverseBalancesForCancellation(1L, 2L, transfer.getAmount());
        verify(saveTransferPort).save(transfer);
        verify(eventPublisherPort).publish(any());
    }

    @Test
    void shouldPropagateExceptionWhenTransferNotFound() {
        when(loadTransferPort.findByIdWithLock(999L)).thenReturn(Optional.empty());

        assertThrows(com.bank.app.transfer.exception.TransferNotFoundException.class,
                () -> decorator.execute(999L));

        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldBeAnnotatedWithTransactional() {
        assertTrue(decorator.getClass().isAnnotationPresent(Transactional.class),
                "Decorator must be @Transactional");
    }

    @Test
    void executeMethodShouldBeAnnotatedWithRetryable() throws Exception {
        Method executeMethod = decorator.getClass().getMethod("execute", Long.class);
        Retryable retryable = executeMethod.getAnnotation(Retryable.class);
        assertNotNull(retryable, "execute() must be @Retryable");
        assertTrue(retryable.retryFor().length > 0);
        assertEquals(ConcurrencyFailureException.class, retryable.retryFor()[0]);
    }
}
