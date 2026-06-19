package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.*;

import com.bank.app.transfer.exception.SameAccountTransferException;
import com.bank.app.account.exception.InvalidIbanException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.bank.app.common.application.port.out.EventPublisherPort;
import org.springframework.retry.annotation.Retryable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class PlaceTransferUseCaseTest {

    @Mock private AccountOperationPort accountOperationPort;
    @Mock private SaveTransferPort saveTransferPort;
    @Mock private EventPublisherPort eventPublisherPort;

    private PlaceTransferUseCase placeTransferUseCase;

    @BeforeEach
    void setUp() {
        placeTransferUseCase = new PlaceTransferUseCase(accountOperationPort,
                saveTransferPort,
                eventPublisherPort,
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
        when(accountOperationPort.getAccountInfoForTransfer(senderIban))
                .thenReturn(new AccountInfo(senderId, senderUserId, "TRY", senderActive));
        when(accountOperationPort.getAccountInfoForTransfer(receiverIban))
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

        verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(saveTransferPort, times(1)).save(transferCaptor.capture());
        assertEquals(TransferStatus.COMPLETED, transferCaptor.getValue().getStatus());

        verify(eventPublisherPort).publish(any(TransferCompletedEvent.class));
    }

    @Test
    void shouldPropagateAccessDeniedExceptionFromAccountOperationsPort() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, true,
                "TR290006200000000000000222", 2L, 200L, true);

        doThrow(new AccessDeniedException("Bu hesaptan transfer yapmaya yetkiniz yok."))
                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

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

        mockAccountInfos("TR290006200000000000000111", 1L, 100L, true,
                "TR290006200000000000000111", 1L, 100L, true);

        assertThrows(SameAccountTransferException.class, () -> placeTransferUseCase.execute(request));
        verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
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

        doThrow(new com.bank.app.account.exception.AccountNotActiveException("TR290006200000000000000111"))
                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

        assertThrows(com.bank.app.account.exception.AccountNotActiveException.class,
                () -> placeTransferUseCase.execute(request));
        verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));
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

        doThrow(new com.bank.app.account.exception.AccountNotActiveException("TR290006200000000000000222"))
                .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

        assertThrows(com.bank.app.account.exception.AccountNotActiveException.class,
                () -> placeTransferUseCase.execute(request));
        verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));
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
    void shouldThrowInvalidIbanExceptionWhenSenderIbanHasInvalidFormat() {
        TransferRequest request = new TransferRequest(
                "INVALID_IBAN",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        when(accountOperationPort.getAccountInfoForTransfer("INVALID_IBAN"))
                .thenThrow(new InvalidIbanException("Geçersiz IBAN"));

        assertThrows(InvalidIbanException.class, () -> placeTransferUseCase.execute(request));
        verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
    }

    @Test
    void shouldThrowInvalidIbanExceptionWhenReceiverIbanHasInvalidFormat() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "INVALID_IBAN",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        when(accountOperationPort.getAccountInfoForTransfer("TR290006200000000000000111"))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
        when(accountOperationPort.getAccountInfoForTransfer("INVALID_IBAN"))
                .thenThrow(new InvalidIbanException("Geçersiz IBAN"));

        assertThrows(InvalidIbanException.class, () -> placeTransferUseCase.execute(request));
        verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
    }

    @Test
    void shouldHaveTransactionalAnnotationOnClass() {
        Transactional transactional = PlaceTransferUseCase.class.getAnnotation(Transactional.class);
        assertNotNull(transactional, "PlaceTransferUseCase should be annotated with @Transactional");
    }
}
