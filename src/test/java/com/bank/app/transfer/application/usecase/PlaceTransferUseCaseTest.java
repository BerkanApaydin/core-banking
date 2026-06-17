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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
class PlaceTransferUseCaseTest {

    private AccountOperationsPort accountOperationsPort;
    private SaveTransferPort saveTransferPort;
    private ApplicationEventPublisher eventPublisher;
    private AuditService auditService;
    private TransferDomainService transferDomainService;
    private PlaceTransferUseCase placeTransferUseCase;

    @BeforeEach
    void setUp() {
        accountOperationsPort = mock(AccountOperationsPort.class);
        saveTransferPort = mock(SaveTransferPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        auditService = mock(AuditService.class);
        transferDomainService = new TransferDomainService();
        placeTransferUseCase = new PlaceTransferUseCase(
                accountOperationsPort,
                saveTransferPort,
                auditService,
                eventPublisher,
                transferDomainService);
    }

    @Test
    void shouldPlaceTransferSuccessfully() {
        TransferRequest request = new TransferRequest(
                "TR290006200000000000000111",
                "TR290006200000000000000222",
                new BigDecimal("200.00"),
                Money.Currency.TRY);

        AccountInfo senderInfo = new AccountInfo(1L, 100L, "TRY", true);
        AccountInfo receiverInfo = new AccountInfo(2L, 200L, "TRY", true);

        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000111")).thenReturn(senderInfo);
        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000222")).thenReturn(receiverInfo);

        when(saveTransferPort.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new Transfer(10L, t.getSenderAccountId(), t.getReceiverAccountId(), t.getAmount(), t.getStatus(),
                    t.getCreatedAt());
        });

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

        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000111")).thenReturn(senderInfo);
        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000222")).thenReturn(receiverInfo);

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

        AccountInfo senderInfo = new AccountInfo(1L, 100L, "TRY", false);
        AccountInfo receiverInfo = new AccountInfo(2L, 200L, "TRY", true);

        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000111")).thenReturn(senderInfo);
        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000222")).thenReturn(receiverInfo);

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

        AccountInfo senderInfo = new AccountInfo(1L, 100L, "TRY", true);
        AccountInfo receiverInfo = new AccountInfo(2L, 200L, "TRY", false);

        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000111")).thenReturn(senderInfo);
        when(accountOperationsPort.getAccountInfoForTransfer("TR290006200000000000000222")).thenReturn(receiverInfo);

        assertThrows(AccountNotActiveException.class, () -> placeTransferUseCase.execute(request));
        verify(accountOperationsPort, never()).debitAndCredit(anyLong(), anyLong(), any());
    }
}
