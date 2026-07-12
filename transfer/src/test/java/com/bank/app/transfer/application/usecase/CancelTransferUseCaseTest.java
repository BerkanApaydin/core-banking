package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.transfer.domain.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.in.CancelTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.transfer.domain.exception.TransferAlreadyCancelledException;
import com.bank.app.transfer.domain.exception.TransferNotCancellableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelTransferUseCaseTest {

    @Mock
    private LoadTransferPort loadTransferPort;
    @Mock
    private SaveTransferPort saveTransferPort;
    @Mock
    private AccountAclPort accountAclPort;
    @Mock
    private AuditEventPort auditEventPort;
    @Mock
    private UserContextService userContextService;
    @Mock
    private DomainEventPublisherService domainEventPublisherService;
    @Mock
    private ClockProviderPort clockProvider;

    private CancelTransferUseCase cancelTransferUseCase;

    @BeforeEach
    void setUp() {
        lenient().when(clockProvider.clock()).thenReturn(Clock.systemDefaultZone());
        TransferAuthorizationService transferAuthorizationService = new TransferAuthorizationService(
                accountAclPort, userContextService);
        cancelTransferUseCase = new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort,
                accountAclPort, auditEventPort, transferAuthorizationService, domainEventPublisherService, clockProvider, 72);
    }

    @Test
    void shouldCancelTransferSuccessfully() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Long receiverAccountId = 20L;
        Money amount = new Money(new BigDecimal("100.00"), Currency.TRY);

        Transfer transfer = createCompletedTransfer(transferId, senderAccountId, receiverAccountId, amount);

        when(loadTransferPort.findByIdWithLock(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId))
                .thenReturn(new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE"));
        when(accountAclPort.reverseBalancesForCancellation(any(), any(), any()))
                .thenReturn(List.of(mock(DomainEvent.class), mock(DomainEvent.class)));

        cancelTransferUseCase.execute(transferId);

        verify(userContextService).checkUserAuthorization(eq(100L), anyString());
        verify(accountAclPort).reverseBalancesForCancellation(senderAccountId, receiverAccountId, amount);
        verify(saveTransferPort).save(transfer);
        verify(domainEventPublisherService, times(2)).publish(any());
        verify(domainEventPublisherService).publishEvents(transfer);
        verify(auditEventPort).publish(any());
        assertEquals(TransferStatus.CANCELLED, transfer.getStatus());
    }

    @Test
    void shouldThrowTransferNotFoundExceptionWhenTransferDoesNotExist() {
        Long transferId = 1L;
        when(loadTransferPort.findByIdWithLock(transferId)).thenReturn(Optional.empty());

        assertThrows(TransferNotFoundException.class, () -> cancelTransferUseCase.execute(transferId));
        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
    }

    @Test
    void shouldThrowAuthorizationExceptionWhenUserNotOwner() {
        Long transferId = 1L;
        Long senderAccountId = 10L;
        Transfer transfer = createCompletedTransfer(transferId, senderAccountId, 20L,
                new Money(new BigDecimal("100.00"), Currency.TRY));

        when(loadTransferPort.findByIdWithLock(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(senderAccountId))
                .thenReturn(new AccountInfo(senderAccountId, 100L, "TRY", "ACTIVE"));
        doThrow(new AuthorizationException("yetki yok")).when(userContextService)
                .checkUserAuthorization(eq(100L), anyString());

        assertThrows(AuthorizationException.class, () -> cancelTransferUseCase.execute(transferId));
        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
    }

    @Test
    void shouldThrowWhenTransferAlreadyCancelled() {
        Long transferId = 1L;
        Transfer transfer = createCompletedTransfer(transferId, 10L, 20L,
                new Money(new BigDecimal("100.00"), Currency.TRY));
        transfer.cancel(Clock.systemDefaultZone(), 24);

        when(loadTransferPort.findByIdWithLock(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(10L))
                .thenReturn(new AccountInfo(10L, 100L, "TRY", "ACTIVE"));

        assertThrows(TransferAlreadyCancelledException.class,
                () -> cancelTransferUseCase.execute(transferId));
    }

    @Test
    void shouldThrowWhenTransferNotCompleted() {
        Long transferId = 1L;
        Money amount = new Money(new BigDecimal("100.00"), Currency.TRY);
        Transfer transfer = Transfer.create(10L, 20L, amount, Clock.systemDefaultZone());

        when(loadTransferPort.findByIdWithLock(transferId)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(10L))
                .thenReturn(new AccountInfo(10L, 100L, "TRY", "ACTIVE"));

        assertThrows(TransferNotCancellableException.class,
                () -> cancelTransferUseCase.execute(transferId));
    }

    private static Transfer createCompletedTransfer(Long id, Long senderId, Long receiverId, Money amount) {
        Transfer transfer = new Transfer(id, senderId, receiverId, amount,
                TransferStatus.COMPLETED, LocalDateTime.now().minusHours(1));
        return transfer;
    }
}
