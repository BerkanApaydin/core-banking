package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.port.out.AuditEventPort;
import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.ResourceOwnershipPolicy;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.AccountId;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.exception.AuthorizationException;
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
import com.bank.app.transfer.domain.exception.TransferNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cancellation-window and idempotency edge cases of the cancel use case.
 *
 * <p>The happy-path tests live in {@code CancelTransferUseCaseTest}; these
 * cover the branches that only a transfer older than the configured window or
 * a repeated cancel request can reach.
 */
@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
@DisplayName("CancelTransferUseCaseImpl edge cases")
class CancelTransferUseCaseEdgeCaseTest {

    private static final Duration CANCELLATION_WINDOW = Duration.ofHours(72);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);
    private static final Money AMOUNT = Money.of("100.00", Currency.TRY);
    private static final AccountId SENDER = new AccountId(10L);
    private static final AccountId RECEIVER = new AccountId(20L);
    private static final Long TRANSFER_ID = 1L;

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
        TransferAuthorizationService transferAuthorizationService = new TransferAuthorizationService(
                accountAclPort, new ResourceOwnershipPolicy(userContextService));
        cancelTransferUseCase = new CancelTransferUseCaseImpl(loadTransferPort, saveTransferPort,
                accountAclPort, auditEventPort, transferAuthorizationService, domainEventPublisherService,
                clockProvider, CANCELLATION_WINDOW);
    }

    private void stubClock() {
        when(clockProvider.clock()).thenReturn(FIXED_CLOCK);
    }

    private void stubSenderAccount() {
        when(accountAclPort.getAccountInfo(SENDER))
                .thenReturn(new AccountInfo(SENDER, 100L, "TRY", "ACTIVE"));
    }

    private static Transfer completedTransferCreatedAt(LocalDateTime createdAt) {
        return new Transfer(TRANSFER_ID, SENDER, RECEIVER, AMOUNT, TransferStatus.COMPLETED, createdAt);
    }

    @Test
    @DisplayName("should reject a cancellation whose window has expired without refunding anything")
    void shouldRejectCancellationWhenWindowHasExpired() {
        stubClock();
        // Created 100 hours ago, the configured window is 72 hours.
        Transfer transfer = completedTransferCreatedAt(
                LocalDateTime.ofInstant(FIXED_CLOCK.instant().minusSeconds(100 * 3600L), ZoneOffset.UTC));

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        stubSenderAccount();

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isExactlyInstanceOf(TransferNotCancellableException.class)
                .hasMessageContaining("cancellation window has passed");

        // The cancel endpoint exists to move money back: an expired window
        // must not debit/credit anything, must not persist a cancellation and
        // must not announce one.
        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
        verify(domainEventPublisherService, never()).publishEvents(any());
        verifyNoInteractions(auditEventPort);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("should report a distinct error code for an expired window")
    void shouldReportDistinctErrorCodeForExpiredWindow() {
        stubClock();
        Transfer transfer = completedTransferCreatedAt(
                LocalDateTime.ofInstant(FIXED_CLOCK.instant().minusSeconds(100 * 3600L), ZoneOffset.UTC));

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        stubSenderAccount();

        // The expired-window failure and the not-cancellable failure are two
        // distinct client-visible outcomes and must not collapse into one
        // error code.
        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isInstanceOfSatisfying(TransferNotCancellableException.class, failure ->
                        assertThat(failure.getErrorCode())
                                .isEqualTo("TRANSFER_CANCELLATION_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("should not refund a second time when the transfer was already cancelled")
    void shouldNotRefundTwiceWhenTransferAlreadyCancelled() {
        stubClock();
        Transfer transfer = completedTransferCreatedAt(
                LocalDateTime.ofInstant(FIXED_CLOCK.instant().minusSeconds(3600L), ZoneOffset.UTC));
        // First cancellation already flipped the status and registered the event.
        transfer.cancel(FIXED_CLOCK, CANCELLATION_WINDOW);

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        stubSenderAccount();

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isExactlyInstanceOf(TransferAlreadyCancelledException.class);

        // Double-refund guard: a repeated cancel request must not move money a
        // second time and must not publish a second cancellation.
        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
        verify(domainEventPublisherService, never()).publishEvents(any());
        verifyNoInteractions(auditEventPort);
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(transfer.getDomainEvents()).hasSize(1);
    }

    @Test
    @DisplayName("should not persist or publish anything when the balance reversal fails")
    void shouldNotPersistOrPublishWhenBalanceReversalFails() {
        stubClock();
        Transfer transfer = completedTransferCreatedAt(
                LocalDateTime.ofInstant(FIXED_CLOCK.instant().minusSeconds(3600L), ZoneOffset.UTC));

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        stubSenderAccount();
        doThrow(new RuntimeException("Receiver already spent the funds"))
                .when(accountAclPort).reverseBalancesForCancellation(any(), any(), any());

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Receiver already spent the funds");

        // The reversal failed before any write: neither the transfer nor any
        // cancellation event may leave the use case, so no observer can act on
        // a refund that did not happen.
        verify(saveTransferPort, never()).save(any());
        verify(domainEventPublisherService, never()).publishEvents(any());
        verifyNoInteractions(auditEventPort);
    }

    @Test
    @DisplayName("should not refund a transfer that is still pending")
    void shouldNotRefundPendingTransfer() {
        stubClock();
        Transfer transfer = new Transfer(TRANSFER_ID, SENDER, RECEIVER, AMOUNT,
                TransferStatus.PENDING, LocalDateTime.ofInstant(FIXED_CLOCK.instant(), ZoneOffset.UTC));

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        stubSenderAccount();

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isInstanceOfSatisfying(TransferNotCancellableException.class, failure ->
                        assertThat(failure.getErrorCode()).isEqualTo("TRANSFER_NOT_CANCELLABLE"));

        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
    }

    @Test
    @DisplayName("should not refund a transfer that has already failed")
    void shouldNotRefundFailedTransfer() {
        stubClock();
        Transfer transfer = new Transfer(TRANSFER_ID, SENDER, RECEIVER, AMOUNT,
                TransferStatus.FAILED, LocalDateTime.ofInstant(FIXED_CLOCK.instant().minusSeconds(3600L), ZoneOffset.UTC));

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        stubSenderAccount();

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isExactlyInstanceOf(TransferNotCancellableException.class);

        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    @DisplayName("should not refund when the transfer does not exist")
    void shouldNotRefundUnknownTransfer() {
        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isExactlyInstanceOf(TransferNotFoundException.class);

        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
        verifyNoInteractions(auditEventPort);
    }

    @Test
    @DisplayName("should not refund when the caller is not the owner of the sender account")
    void shouldNotRefundWhenCallerIsNotOwner() {
        Transfer transfer = completedTransferCreatedAt(
                LocalDateTime.ofInstant(FIXED_CLOCK.instant().minusSeconds(3600L), ZoneOffset.UTC));

        when(loadTransferPort.findByIdForUpdate(TRANSFER_ID)).thenReturn(Optional.of(transfer));
        when(accountAclPort.getAccountInfo(SENDER))
                .thenReturn(new AccountInfo(SENDER, 100L, "TRY", "ACTIVE"));
        doThrow(new AuthorizationException("yetki yok"))
                .when(userContextService).checkUserAuthorization(eq(100L), anyString());

        assertThatThrownBy(() -> cancelTransferUseCase.execute(TRANSFER_ID))
                .isExactlyInstanceOf(AuthorizationException.class);

        verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
        verify(saveTransferPort, never()).save(any());
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }
}
