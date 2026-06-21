package com.bank.app.transfer.infrastructure.decorator;

import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaceTransferUseCaseDecoratorTest {

    @Mock
    private AccountOperationPort accountOperationPort;
    @Mock
    private SaveTransferPort saveTransferPort;
    @Mock
    private EventPublisherPort eventPublisherPort;

    private PlaceTransferUseCaseDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new PlaceTransferUseCaseDecorator(
                accountOperationPort, saveTransferPort, eventPublisherPort, new TransferDomainService());
    }

    @Test
    void shouldDelegateAndReturnResponse() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111", "TR290006200000000000000222",
                new BigDecimal("200.00"), Money.Currency.TRY);

        when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000111"))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
        when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000222"))
                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
        when(saveTransferPort.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new Transfer(10L, t.getSenderAccountId(), t.getReceiverAccountId(),
                    t.getAmount(), t.getStatus(), t.getCreatedAt());
        });

        TransferResponse response = decorator.execute(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("COMPLETED", response.status());

        verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));
        verify(saveTransferPort).save(any(Transfer.class));
        verify(eventPublisherPort).publish(any());
    }

    @Test
    void shouldBeAnnotatedWithTransactional() {
        assertTrue(decorator.getClass().isAnnotationPresent(Transactional.class),
                "Decorator must be @Transactional");
    }

    @Test
    void executeMethodShouldBeAnnotatedWithRetryable() throws Exception {
        Method executeMethod = decorator.getClass().getMethod("execute", TransferRequest.class);
        Retryable retryable = executeMethod.getAnnotation(Retryable.class);
        assertNotNull(retryable, "execute() must be @Retryable");
        assertTrue(retryable.retryFor().length > 0);
        assertEquals(ConcurrencyFailureException.class, retryable.retryFor()[0]);
    }
}
