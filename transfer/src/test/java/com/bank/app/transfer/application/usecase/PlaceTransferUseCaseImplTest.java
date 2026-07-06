package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.service.DomainEventPublisherService;
import com.bank.app.common.application.service.UserContextService;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.event.DomainEvent;
import com.bank.app.common.domain.exception.InvalidIbanException;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferDomainService;
import com.bank.app.transfer.domain.TransferStatus;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import com.bank.app.common.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceTransferUseCaseImpl")
class PlaceTransferUseCaseImplTest {

    @Mock
    private AccountAclPort accountAclPort;

    @Mock
    private SaveTransferPort saveTransferPort;

    @Mock
    private UserContextService userContextService;

    @Mock
    private DomainEventPublisherService domainEventPublisherService;

    private TransferDomainService transferDomainService;

    private PlaceTransferUseCase placeTransferUseCase;

    private static final String SENDER_IBAN = "TR290006200000000000000111";
    private static final String RECEIVER_IBAN = "TR290006200000000000000222";
    private static final Long SENDER_ACCOUNT_ID = 1L;
    private static final Long RECEIVER_ACCOUNT_ID = 2L;
    private static final Long SENDER_USER_ID = 100L;
    private static final Long RECEIVER_USER_ID = 200L;
    private static final BigDecimal AMOUNT = new BigDecimal("250.00");

    @Captor
    private ArgumentCaptor<Transfer> transferCaptor;

    @BeforeEach
    void setUp() {
        transferDomainService = new TransferDomainService();
        TransferAuthorizationService transferAuthorizationService = new TransferAuthorizationService(
                accountAclPort, userContextService);
        placeTransferUseCase = new PlaceTransferUseCaseImpl(
                accountAclPort, saveTransferPort,
                transferDomainService, transferAuthorizationService, domainEventPublisherService);
    }

    private AccountInfo senderInfo() {
        return new AccountInfo(SENDER_ACCOUNT_ID, SENDER_USER_ID, "TRY", "ACTIVE");
    }

    private AccountInfo receiverInfo() {
        return new AccountInfo(RECEIVER_ACCOUNT_ID, RECEIVER_USER_ID, "TRY", "ACTIVE");
    }

    private TransferRequest validRequest() {
        return new TransferRequest(SENDER_IBAN, RECEIVER_IBAN, AMOUNT, Currency.TRY);
    }

    private void stubAccountLookup() {
        when(accountAclPort.getAccountInfoForTransfer(SENDER_IBAN)).thenReturn(senderInfo());
        when(accountAclPort.getAccountInfoForTransfer(RECEIVER_IBAN)).thenReturn(receiverInfo());
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should place transfer successfully")
        void shouldPlaceTransferSuccessfully() {
            stubAccountLookup();
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());

            when(saveTransferPort.save(any(Transfer.class)))
                    .thenAnswer(invocation -> {
                        Transfer t = invocation.getArgument(0);
                        return new Transfer(42L, t.getSenderAccountId(), t.getReceiverAccountId(),
                                t.getAmount(), t.getStatus(), t.getCreatedAt(), 1L);
                    });
            when(accountAclPort.debitAndCredit(any(), any(), any()))
                    .thenReturn(List.of(mock(DomainEvent.class), mock(DomainEvent.class)));

            TransferResponse response = placeTransferUseCase.execute(validRequest());

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.amount()).isEqualByComparingTo("250.00");
            assertThat(response.currency()).isEqualTo("TRY");
            assertThat(response.senderIban()).isEqualTo(SENDER_IBAN);
            assertThat(response.receiverIban()).isEqualTo(RECEIVER_IBAN);
            assertThat(response.status()).isEqualTo("COMPLETED");

            verify(accountAclPort).getAccountInfoForTransfer(SENDER_IBAN);
            verify(accountAclPort).getAccountInfoForTransfer(RECEIVER_IBAN);
            verify(userContextService).checkUserAuthorization(SENDER_USER_ID,
                    "You are not authorized to transfer from this account.");
            verify(accountAclPort).debitAndCredit(SENDER_ACCOUNT_ID, RECEIVER_ACCOUNT_ID,
                    new Money(AMOUNT, Currency.TRY));
            verify(saveTransferPort, times(2)).save(any(Transfer.class));
            verify(domainEventPublisherService, times(2)).publish(any());
            verify(domainEventPublisherService).publishEvents(any());
        }

        @Test
        @DisplayName("should create a PENDING transfer initially then mark COMPLETED")
        void shouldCreatePendingThenComplete() {
            stubAccountLookup();
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());

            when(saveTransferPort.save(any(Transfer.class)))
                    .thenAnswer(invocation -> {
                        Transfer t = invocation.getArgument(0);
                        return new Transfer(42L, t.getSenderAccountId(), t.getReceiverAccountId(),
                                t.getAmount(), t.getStatus(), t.getCreatedAt(), 1L);
                    });
            when(accountAclPort.debitAndCredit(any(), any(), any()))
                    .thenReturn(List.of(mock(DomainEvent.class), mock(DomainEvent.class)));

            placeTransferUseCase.execute(validRequest());

            verify(saveTransferPort, times(2)).save(transferCaptor.capture());
            Transfer firstSaved = transferCaptor.getAllValues().get(0);
            assertThat(firstSaved.getStatus()).isEqualTo(TransferStatus.PENDING);
            Transfer secondSaved = transferCaptor.getAllValues().get(1);
            assertThat(secondSaved.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should throw when request is null")
        void shouldThrowOnNullRequest() {
            assertThatThrownBy(() -> placeTransferUseCase.execute(null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Request must not be null");
        }

        @Test
        @DisplayName("should throw when IBAN is invalid even before calling ACL")
        void shouldThrowOnInvalidSenderIban() {
            when(accountAclPort.getAccountInfoForTransfer(anyString()))
                    .thenThrow(new InvalidIbanException("INVALID"));

            TransferRequest request = new TransferRequest("INVALID", RECEIVER_IBAN, AMOUNT, Currency.TRY);
            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(InvalidIbanException.class);
        }

        @Test
        @DisplayName("should throw SameAccountTransferException when sender and receiver are same")
        void shouldThrowOnSameAccount() {
            AccountInfo sameInfo = new AccountInfo(SENDER_ACCOUNT_ID, SENDER_USER_ID, "TRY", "ACTIVE");
            when(accountAclPort.getAccountInfoForTransfer(SENDER_IBAN)).thenReturn(sameInfo);
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());

            TransferRequest request = new TransferRequest(SENDER_IBAN, SENDER_IBAN, AMOUNT, Currency.TRY);
            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(SameAccountTransferException.class);
        }

        @Test
        @DisplayName("should throw when sender currency does not match amount currency")
        void shouldThrowOnSenderCurrencyMismatch() {
            AccountInfo usdSender = new AccountInfo(SENDER_ACCOUNT_ID, SENDER_USER_ID, "USD", "ACTIVE");
            when(accountAclPort.getAccountInfoForTransfer(SENDER_IBAN)).thenReturn(usdSender);
            when(accountAclPort.getAccountInfoForTransfer(RECEIVER_IBAN)).thenReturn(receiverInfo());
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    @DisplayName("authorization")
    class Authorization {

        @Test
        @DisplayName("should throw when sender is not authorized")
        void shouldThrowWhenNotAuthorized() {
            when(accountAclPort.getAccountInfoForTransfer(SENDER_IBAN)).thenReturn(senderInfo());
            doThrow(new org.springframework.security.access.AccessDeniedException("yetki yok"))
                    .when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            verify(saveTransferPort, never()).save(any());
            verify(accountAclPort, never()).debitAndCredit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("failure handling")
    class FailureHandling {

        @Test
        @DisplayName("should throw and not compensate when debitAndCredit fails after PENDING save")
        void shouldNotCompensateOnDebitFailure() {
            stubAccountLookup();
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());
            when(saveTransferPort.save(any(Transfer.class)))
                    .thenAnswer(invocation -> {
                        Transfer t = invocation.getArgument(0);
                        return new Transfer(42L, t.getSenderAccountId(), t.getReceiverAccountId(),
                                t.getAmount(), t.getStatus(), t.getCreatedAt(), 1L);
                    });
            doThrow(new RuntimeException("Account service unavailable"))
                    .when(accountAclPort).debitAndCredit(any(), any(), any());

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasMessage("Account service unavailable");

            verify(saveTransferPort, times(1)).save(any());
            verify(accountAclPort).debitAndCredit(any(), any(), any());
            verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
            verify(domainEventPublisherService, never()).publish(any());
            verify(domainEventPublisherService, never()).publishEvents(any());
        }

        @Test
        @DisplayName("should throw when receiver account lookup fails")
        void shouldThrowOnReceiverNotFound() {
            when(accountAclPort.getAccountInfoForTransfer(SENDER_IBAN)).thenReturn(senderInfo());
            when(accountAclPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                    .thenThrow(new RuntimeException("Receiver not found"));

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasMessage("Receiver not found");

            verify(accountAclPort).getAccountInfoForTransfer(SENDER_IBAN);
            verify(accountAclPort).getAccountInfoForTransfer(RECEIVER_IBAN);
            verify(saveTransferPort, never()).save(any());
            verify(accountAclPort, never()).debitAndCredit(any(), any(), any());
            verify(domainEventPublisherService, never()).publish(any());
            verify(domainEventPublisherService, never()).publishEvents(any());
        }

        @Test
        @DisplayName("should throw when first save fails before debit")
        void shouldThrowWhenInitialSaveFails() {
            stubAccountLookup();
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());
            when(saveTransferPort.save(any(Transfer.class)))
                    .thenThrow(new RuntimeException("Database unavailable"));

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasMessage("Database unavailable");

            verify(saveTransferPort, times(1)).save(any(Transfer.class));
            verify(accountAclPort, never()).debitAndCredit(any(), any(), any());
            verify(domainEventPublisherService, never()).publish(any());
            verify(domainEventPublisherService, never()).publishEvents(any());
        }

        @Test
        @DisplayName("should throw and not compensate when event publishing fails after complete")
        void shouldNotCompensateWhenEventPublishFails() {
            stubAccountLookup();
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());
            when(saveTransferPort.save(any(Transfer.class)))
                    .thenAnswer(invocation -> {
                        Transfer t = invocation.getArgument(0);
                        return new Transfer(42L, t.getSenderAccountId(), t.getReceiverAccountId(),
                                t.getAmount(), t.getStatus(), t.getCreatedAt(), 1L);
                    });
            when(accountAclPort.debitAndCredit(any(), any(), any()))
                    .thenReturn(List.of(mock(DomainEvent.class), mock(DomainEvent.class)));
            doThrow(new RuntimeException("Event bus unavailable"))
                    .when(domainEventPublisherService).publishEvents(any());

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasMessage("Event bus unavailable");

            verify(accountAclPort).debitAndCredit(eq(SENDER_ACCOUNT_ID), eq(RECEIVER_ACCOUNT_ID),
                    any(Money.class));
            verify(accountAclPort, never()).reverseBalancesForCancellation(any(), any(), any());
            verify(saveTransferPort, times(2)).save(transferCaptor.capture());
            verify(domainEventPublisherService, times(2)).publish(any());
            verify(domainEventPublisherService).publishEvents(any());
        }

        @Test
        @DisplayName("should throw when receiver currency does not match sender")
        void shouldThrowOnReceiverCurrencyMismatch() {
            AccountInfo trySender = new AccountInfo(SENDER_ACCOUNT_ID, SENDER_USER_ID, "TRY", "ACTIVE");
            AccountInfo usdReceiver = new AccountInfo(RECEIVER_ACCOUNT_ID, RECEIVER_USER_ID, "USD", "ACTIVE");
            when(accountAclPort.getAccountInfoForTransfer(SENDER_IBAN)).thenReturn(trySender);
            when(accountAclPort.getAccountInfoForTransfer(RECEIVER_IBAN)).thenReturn(usdReceiver);
            doNothing().when(userContextService).checkUserAuthorization(eq(SENDER_USER_ID), anyString());

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isInstanceOf(CurrencyMismatchException.class);

            verify(accountAclPort, never()).debitAndCredit(any(), any(), any());
            verify(saveTransferPort, never()).save(any());
        }
    }
}
