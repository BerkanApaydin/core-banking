package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.AccountOperationsPort;
import com.bank.app.transfer.application.port.AccountOperationsPort.AccountInfo;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.SaveTransferPort;
import com.bank.app.transfer.domain.*;
import com.bank.app.audit.application.service.AuditService;
import com.bank.app.common.exception.SameAccountTransferException;
import com.bank.app.common.exception.AccountNotActiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PlaceTransferUseCaseTest {

    @Mock private AccountOperationsPort accountOperationsPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditService auditService;

    private PlaceTransferUseCase placeTransferUseCase;

    @BeforeEach
    void setUp() {
        placeTransferUseCase = new PlaceTransferUseCase(
                accountOperationsPort,
                saveTransferPort,
                auditService,
                eventPublisher,
                new TransferDomainService());
    }

    private void mockSaveReturnsCopy() {
        when(saveTransferPort.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new Transfer(10L, t.getSenderAccountId(), t.getReceiverAccountId(), t.getAmount(), t.getStatus(),
                    t.getCreatedAt());
        });
    }

    private void mockAccountInfos(String senderIban, long senderId, long senderUserId, boolean senderActive,
                                   String receiverIban, long receiverId, long receiverUserId, boolean receiverActive) {
        when(accountOperationsPort.getAccountInfoForTransfer(senderIban))
                .thenReturn(new AccountInfo(senderId, senderUserId, "TRY", senderActive));
        when(accountOperationsPort.getAccountInfoForTransfer(receiverIban))
                .thenReturn(new AccountInfo(receiverId, receiverUserId, "TRY", receiverActive));
    }

    @Test
    void shouldPlaceTransferSuccessfully() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, true,
                "TR290006200000000000000222", 2L, 200L, true);
        mockSaveReturnsCopy();

        TransferResponse response = placeTransferUseCase.execute(request);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals("COMPLETED", response.status());
        assertEquals(new BigDecimal("200.00"), response.amount());

        verify(accountOperationsPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(saveTransferPort, times(1)).save(transferCaptor.capture());
        assertEquals(TransferStatus.COMPLETED, transferCaptor.getValue().getStatus());

        verify(eventPublisher).publishEvent(any(TransferCompletedEvent.class));
    }

    @Test
    void shouldPropagateAccessDeniedExceptionFromAccountOperationsPort() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        AccountInfo senderInfo = new AccountInfo(1L, 100L, "TRY", true);
        AccountInfo receiverInfo = new AccountInfo(2L, 200L, "TRY", true);

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, true,
                "TR290006200000000000000222", 2L, 200L, true);

        doThrow(new AccessDeniedException("Bu hesaptan transfer yapmaya yetkiniz yok."))
                .when(accountOperationsPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

        assertThrows(AccessDeniedException.class, () -> placeTransferUseCase.execute(request));

        verifyNoInteractions(saveTransferPort);
    }

    @Test
    void shouldThrowNullPointerExceptionWhenRequestIsNull() {
        assertThrows(NullPointerException.class, () -> placeTransferUseCase.execute(null));
    }

    @Test
    void shouldThrowSameAccountTransferExceptionWhenIbansAreSame() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000111",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        assertThrows(SameAccountTransferException.class, () -> placeTransferUseCase.execute(request));
        verifyNoInteractions(accountOperationsPort);
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenSenderIsPassive() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, false,
                "TR290006200000000000000222", 2L, 200L, true);

        assertThrows(AccountNotActiveException.class, () -> placeTransferUseCase.execute(request));
        verify(accountOperationsPort, never()).debitAndCredit(anyLong(), anyLong(), any());
    }

    @Test
    void shouldThrowAccountNotActiveExceptionWhenReceiverIsPassive() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, true,
                "TR290006200000000000000222", 2L, 200L, false);

        assertThrows(AccountNotActiveException.class, () -> placeTransferUseCase.execute(request));
        verify(accountOperationsPort, never()).debitAndCredit(anyLong(), anyLong(), any());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenSenderIbanIsNull() {
        TransferRequest request = new TransferRequest(
                null,
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        assertThrows(NullPointerException.class, () -> placeTransferUseCase.execute(request));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenReceiverIbanIsNull() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                null,
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        assertThrows(NullPointerException.class, () -> placeTransferUseCase.execute(request));
    }

    @Test
    void shouldTransferResponseContainCorrectFieldTypes() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, true,
                "TR290006200000000000000222", 2L, 200L, true);
        mockSaveReturnsCopy();

        TransferResponse response = placeTransferUseCase.execute(request);

        assertNotNull(response.createdAt());
        assertEquals(1L, response.senderAccountId());
        assertEquals(2L, response.receiverAccountId());
        assertEquals("TRY", response.currency());
    }

    @Test
    void shouldHaveRetryableAnnotationOnExecuteMethod() throws Exception {
        Method method = PlaceTransferUseCase.class.getMethod("execute", TransferRequest.class);
        Retryable retryable = method.getAnnotation(Retryable.class);
        assertNotNull(retryable, "execute method should be annotated with @Retryable");
    }

    @Test
    void shouldHaveTransactionalAnnotationOnClass() {
        Transactional transactional = PlaceTransferUseCase.class.getAnnotation(Transactional.class);
        assertNotNull(transactional, "PlaceTransferUseCase should be annotated with @Transactional");
    }
}
