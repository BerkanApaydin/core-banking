package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.exception.CurrencyMismatchException;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
@DisplayName("TransferDomainService")
class TransferDomainServiceTest {

    private final TransferDomainService transferDomainService = new TransferDomainService();

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("should create transfer successfully")
        void shouldCreateTransferSuccessfully() {
            TransferParticipants participants = new TransferParticipants(
                1L, "TR290006200000000000000111", Currency.TRY,
                2L, "TR290006200000000000000222", Currency.TRY);
            Transfer transfer = transferDomainService.validateAndCreateTransfer(
                    participants, Money.of("300.00", Currency.TRY));

            assertThat(transfer).isNotNull();
            assertThat(transfer.getSenderAccountId()).isEqualTo(1L);
            assertThat(transfer.getReceiverAccountId()).isEqualTo(2L);
            assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("should throw SameAccountTransferException when IDs are equal")
        void shouldThrowSameAccountTransferExceptionWhenIdsAreEqual() {
            TransferParticipants participants = new TransferParticipants(
                1L, "TR1", Currency.TRY,
                1L, "TR2", Currency.TRY);

            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(
                    participants, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(SameAccountTransferException.class)
                    .hasMessage("Aynı hesaba transfer yapılamaz: TR1");
        }

        @Test
        @DisplayName("should throw SameAccountTransferException when IBANs are equal ignoring case")
        void shouldThrowSameAccountTransferExceptionWhenIbansAreEqualIgnoringCase() {
            TransferParticipants participants = new TransferParticipants(
                1L, "tr123", Currency.TRY,
                2L, "TR123", Currency.TRY);

            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(
                    participants, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(SameAccountTransferException.class)
                    .hasMessage("Aynı hesaba transfer yapılamaz: tr123");
        }

        @Test
        @DisplayName("should throw NullPointerException when amount is null")
        void shouldThrowNullPointerExceptionWhenAmountIsNull() {
            TransferParticipants participants = new TransferParticipants(
                1L, "TR1", Currency.TRY,
                2L, "TR2", Currency.TRY);

            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(participants, null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Transfer tutarı null olamaz");
        }

        @Test
        @DisplayName("should throw NullPointerException when participants is null")
        void shouldThrowNullPointerExceptionWhenParticipantsIsNull() {
            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(null, Money.of("100.00", Currency.TRY)))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Transfer katılımcıları null olamaz");
        }

        @Test
        @DisplayName("should throw CurrencyMismatchException when sender currency mismatches")
        void shouldThrowCurrencyMismatchExceptionWhenSenderCurrencyMismatches() {
            TransferParticipants participants = new TransferParticipants(
                1L, "TR1", Currency.USD,
                2L, "TR2", Currency.TRY);

            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(
                    participants, Money.of("100.00", Currency.TRY)))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessage("Gönderici hesap para birimi (USD) ile transfer tutarı para birimi (TRY) eşleşmiyor.");
        }

        @Test
        @DisplayName("should throw CurrencyMismatchException when receiver currency mismatches")
        void shouldThrowCurrencyMismatchExceptionWhenReceiverCurrencyMismatches() {
            TransferParticipants participants = new TransferParticipants(
                1L, "TR1", Currency.TRY,
                2L, "TR2", Currency.USD);

            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(
                    participants, Money.of("100.00", Currency.TRY)))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessage("Alıcı hesap para birimi (USD) ile transfer tutarı para birimi (TRY) eşleşmiyor.");
        }

        @Test
        @DisplayName("should throw when sender IBAN is null in participants")
        void shouldThrowWhenSenderIbanIsNull() {
            assertThatThrownBy(() -> new TransferParticipants(
                    1L, null, Currency.TRY,
                    2L, "TR2", Currency.TRY))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Gönderici IBAN null olamaz");
        }

        @Test
        @DisplayName("should throw when receiver IBAN is null in participants")
        void shouldThrowWhenReceiverIbanIsNull() {
            assertThatThrownBy(() -> new TransferParticipants(
                    1L, "TR1", Currency.TRY,
                    2L, null, Currency.TRY))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Alıcı IBAN null olamaz");
        }

        @Test
        @DisplayName("should throw when sender currency is null in participants")
        void shouldThrowWhenSenderCurrencyIsNull() {
            assertThatThrownBy(() -> new TransferParticipants(
                    1L, "TR1", null,
                    2L, "TR2", Currency.TRY))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Gönderici para birimi null olamaz");
        }

        @Test
        @DisplayName("should throw when receiver currency is null in participants")
        void shouldThrowWhenReceiverCurrencyIsNull() {
            assertThatThrownBy(() -> new TransferParticipants(
                    1L, "TR1", Currency.TRY,
                    2L, "TR2", null))
                    .isExactlyInstanceOf(NullPointerException.class)
                    .hasMessage("Alıcı para birimi null olamaz");
        }

        @Test
        @DisplayName("should throw when amount is zero")
        void shouldThrowWhenAmountIsZero() {
            TransferParticipants participants = new TransferParticipants(
                1L, "TR1", Currency.TRY,
                2L, "TR2", Currency.TRY);

            assertThatThrownBy(() -> transferDomainService.validateAndCreateTransfer(
                    participants, Money.of("0.00", Currency.TRY)))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Transfer tutarı sıfır olamaz");
        }
    }
}
