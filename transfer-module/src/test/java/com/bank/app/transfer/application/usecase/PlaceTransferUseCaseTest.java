package com.bank.app.transfer.application.usecase;

import com.bank.app.account.domain.exception.InsufficientBalanceException;
import com.bank.app.common.exception.InvalidIbanException;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.application.dto.TransferRequest;
import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.in.PlaceTransferUseCase;
import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.port.out.SaveTransferPort;
import com.bank.app.transfer.domain.*;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlaceTransferUseCase")
class PlaceTransferUseCaseTest {

    private static final String SENDER_IBAN = "TR290006200000000000000111";
    private static final String RECEIVER_IBAN = "TR290006200000000000000222";
    private static final BigDecimal AMOUNT = new BigDecimal("200.00");

    @Mock
    private AccountOperationPort accountOperationPort;
    @Mock
    private SaveTransferPort saveTransferPort;
    @Mock
    private EventPublisherPort eventPublisherPort;

    private PlaceTransferUseCase placeTransferUseCase;

    @BeforeEach
    void setUp() {
        placeTransferUseCase = new PlaceTransferUseCaseImpl(
                accountOperationPort, saveTransferPort, eventPublisherPort, new TransferDomainService());
    }

    private TransferRequest validRequest() {
        return new TransferRequest(SENDER_IBAN, RECEIVER_IBAN, AMOUNT, Currency.TRY);
    }

    private void mockActiveAccounts() {
        when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
        when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
    }

    private void mockSaveReturnsCopy() {
        when(saveTransferPort.save(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            return new Transfer(10L, t.getSenderAccountId(), t.getReceiverAccountId(),
                    t.getAmount(), t.getStatus(), t.getCreatedAt());
        });
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should place transfer successfully")
        void shouldPlaceTransferSuccessfully() {
            mockActiveAccounts();
            mockSaveReturnsCopy();

            TransferResponse response = placeTransferUseCase.execute(validRequest());

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
            assertThat(response.status()).isEqualTo("COMPLETED");
            assertThat(response.amount()).isEqualByComparingTo("200.00");
            assertThat(response.currency()).isEqualTo("TRY");
            assertThat(response.senderIban()).isEqualTo(SENDER_IBAN);
            assertThat(response.receiverIban()).isEqualTo(RECEIVER_IBAN);
            assertThat(response.createdAt()).isNotNull();

            verify(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

            ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
            verify(saveTransferPort, times(1)).save(transferCaptor.capture());
            assertThat(transferCaptor.getValue().getStatus()).isEqualTo(TransferStatus.COMPLETED);

            verify(eventPublisherPort).publish(any(TransferCompletedEvent.class));
        }

        @Test
        @DisplayName("should publish TransferCompletedEvent")
        void shouldPublishEvent() {
            mockActiveAccounts();
            mockSaveReturnsCopy();

            placeTransferUseCase.execute(validRequest());

            ArgumentCaptor<TransferCompletedEvent> eventCaptor = ArgumentCaptor.forClass(TransferCompletedEvent.class);
            verify(eventPublisherPort).publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue().getTransferId()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should throw NullPointerException when request is null")
        void shouldThrowOnNullRequest() {
            assertThatThrownBy(() -> placeTransferUseCase.execute(null))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when sender IBAN is null")
        void shouldThrowOnNullSenderIban() {
            TransferRequest request = new TransferRequest(null, RECEIVER_IBAN, AMOUNT, Currency.TRY);
            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException when receiver IBAN is null")
        void shouldThrowOnNullReceiverIban() {
            TransferRequest request = new TransferRequest(SENDER_IBAN, null, AMOUNT, Currency.TRY);
            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("IBAN handling")
    class IbanHandling {

        @Test
        @DisplayName("should throw SameAccountTransferException when IBANs are identical")
        void shouldThrowOnSameIban() {
            TransferRequest request = new TransferRequest(SENDER_IBAN, SENDER_IBAN, AMOUNT, Currency.TRY);

            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", true));

            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(SameAccountTransferException.class);
            verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should throw SameAccountTransferException for case-insensitive same IBAN")
        void shouldThrowOnSameIbanIgnoreCase() {
            TransferRequest request = new TransferRequest(SENDER_IBAN, "tr290006200000000000000111", AMOUNT, Currency.TRY);

            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", true));

            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(SameAccountTransferException.class);
            verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should throw InvalidIbanException when sender IBAN is invalid")
        void shouldThrowOnInvalidSenderIban() {
            TransferRequest request = new TransferRequest("INVALID_IBAN", RECEIVER_IBAN, AMOUNT, Currency.TRY);

            when(accountOperationPort.getAccountInfoForTransfer("INVALID_IBAN"))
                    .thenThrow(new InvalidIbanException("Geçersiz IBAN"));

            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(InvalidIbanException.class);
            verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should throw InvalidIbanException when receiver IBAN is invalid")
        void shouldThrowOnInvalidReceiverIban() {
            TransferRequest request = new TransferRequest(SENDER_IBAN, "INVALID_IBAN", AMOUNT, Currency.TRY);

            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
            when(accountOperationPort.getAccountInfoForTransfer("INVALID_IBAN"))
                    .thenThrow(new InvalidIbanException("Geçersiz IBAN"));

            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(InvalidIbanException.class);
            verify(accountOperationPort, never()).debitAndCredit(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should normalize IBAN with spaces")
        void shouldNormalizeIbanWithSpaces() {
            TransferRequest request = new TransferRequest(
                    "TR29 0006 2000 0000 0000 0001 11",
                    "TR29 0006 2000 0000 0000 0002 22",
                    AMOUNT, Currency.TRY);

            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
            when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                    .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
            mockSaveReturnsCopy();

            TransferResponse response = placeTransferUseCase.execute(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
            verify(accountOperationPort).getAccountInfoForTransfer(SENDER_IBAN);
            verify(accountOperationPort).getAccountInfoForTransfer(RECEIVER_IBAN);
        }

        @Test
        @DisplayName("should normalize IBAN with mixed case and spaces")
        void shouldNormalizeMixedCaseIban() {
            TransferRequest request = new TransferRequest(
                    "tr29 0006 2000 0000 0000 0001 11",
                    "TR29 0006 2000 0000 0000 0002 22",
                    AMOUNT, Currency.TRY);

            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
            when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                    .thenReturn(new AccountInfo(2L, 200L, "TRY", true));
            mockSaveReturnsCopy();

            TransferResponse response = placeTransferUseCase.execute(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("account status")
    class AccountStatus {

        @Test
        @DisplayName("should throw when sender account is not active")
        void shouldThrowOnSenderPassive() {
            TransferRequest request = validRequest();
            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", false));
            when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                    .thenReturn(new AccountInfo(2L, 200L, "TRY", true));

            doThrow(new com.bank.app.account.domain.exception.AccountNotActiveException(SENDER_IBAN))
                    .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(com.bank.app.account.domain.exception.AccountNotActiveException.class);
        }

        @Test
        @DisplayName("should throw when receiver account is not active")
        void shouldThrowOnReceiverPassive() {
            TransferRequest request = validRequest();
            when(accountOperationPort.getAccountInfoForTransfer(SENDER_IBAN))
                    .thenReturn(new AccountInfo(1L, 100L, "TRY", true));
            when(accountOperationPort.getAccountInfoForTransfer(RECEIVER_IBAN))
                    .thenReturn(new AccountInfo(2L, 200L, "TRY", false));

            doThrow(new com.bank.app.account.domain.exception.AccountNotActiveException(RECEIVER_IBAN))
                    .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

            assertThatThrownBy(() -> placeTransferUseCase.execute(request))
                    .isExactlyInstanceOf(com.bank.app.account.domain.exception.AccountNotActiveException.class);
        }
    }

    @Nested
    @DisplayName("error propagation")
    class ErrorPropagation {

        @Test
        @DisplayName("should propagate AccessDeniedException")
        void shouldPropagateAccessDenied() {
            mockActiveAccounts();

            doThrow(new AccessDeniedException("Yetkiniz yok."))
                    .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(saveTransferPort);
        }

        @Test
        @DisplayName("should propagate InsufficientBalanceException")
        void shouldPropagateInsufficientBalance() {
            mockActiveAccounts();

            doThrow(new InsufficientBalanceException("Yetersiz bakiye"))
                    .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(InsufficientBalanceException.class);
            verify(saveTransferPort, never()).save(any());
        }

        @Test
        @DisplayName("should propagate ConcurrencyFailureException")
        void shouldPropagateConcurrencyFailure() {
            mockActiveAccounts();

            doThrow(new ConcurrencyFailureException("Optimistic lock"))
                    .when(accountOperationPort).debitAndCredit(eq(1L), eq(2L), any(Money.class));

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(ConcurrencyFailureException.class);
            verify(saveTransferPort, never()).save(any());
        }

        @Test
        @DisplayName("should rollback when event publish fails")
        void shouldRollbackOnEventPublishFailure() {
            mockActiveAccounts();
            mockSaveReturnsCopy();

            doThrow(new RuntimeException("Event publish failed"))
                    .when(eventPublisherPort).publish(any(TransferCompletedEvent.class));

            assertThatThrownBy(() -> placeTransferUseCase.execute(validRequest()))
                    .isExactlyInstanceOf(RuntimeException.class);
        }
    }
}
