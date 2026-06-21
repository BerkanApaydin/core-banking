package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.CurrencyMismatchException;
import com.bank.app.transfer.domain.exception.SameAccountTransferException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferDomainServiceTest {

    private final TransferDomainService transferDomainService = new TransferDomainService();

    @Test
    void shouldCreateTransferSuccessfully() {
        TransferParticipants participants = new TransferParticipants(
            1L, "TR290006200000000000000111", Money.Currency.TRY,
            2L, "TR290006200000000000000222", Money.Currency.TRY);
        Transfer transfer = transferDomainService.validateAndCreateTransfer(
                participants, Money.of("300.00", Money.Currency.TRY));

        assertNotNull(transfer);
        assertEquals(1L, transfer.getSenderAccountId());
        assertEquals(2L, transfer.getReceiverAccountId());
        assertEquals(TransferStatus.PENDING, transfer.getStatus());
    }

    @Test
    void shouldThrowSameAccountTransferExceptionWhenIdsAreEqual() {
        TransferParticipants participants = new TransferParticipants(
            1L, "TR1", Money.Currency.TRY,
            1L, "TR2", Money.Currency.TRY);
        SameAccountTransferException ex = assertThrows(
                SameAccountTransferException.class,
                () -> transferDomainService.validateAndCreateTransfer(
                        participants, Money.of("100.00", Money.Currency.TRY)));
        assertEquals("Aynı hesaba transfer yapılamaz: TR1", ex.getMessage());
    }

    @Test
    void shouldThrowSameAccountTransferExceptionWhenIbansAreEqualIgnoringCase() {
        TransferParticipants participants = new TransferParticipants(
            1L, "tr123", Money.Currency.TRY,
            2L, "TR123", Money.Currency.TRY);
        SameAccountTransferException ex = assertThrows(
                SameAccountTransferException.class,
                () -> transferDomainService.validateAndCreateTransfer(
                        participants, Money.of("100.00", Money.Currency.TRY)));
        assertEquals("Aynı hesaba transfer yapılamaz: tr123", ex.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenAmountIsNull() {
        TransferParticipants participants = new TransferParticipants(
            1L, "TR1", Money.Currency.TRY,
            2L, "TR2", Money.Currency.TRY);
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> transferDomainService.validateAndCreateTransfer(participants, null));
        assertEquals("Transfer tutarı null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenParticipantsIsNull() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> transferDomainService.validateAndCreateTransfer(null, Money.of("100.00", Money.Currency.TRY)));
        assertEquals("Transfer katılımcıları null olamaz", ex.getMessage());
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionWhenSenderCurrencyMismatchesAmountCurrency() {
        TransferParticipants participants = new TransferParticipants(
            1L, "TR1", Money.Currency.USD,
            2L, "TR2", Money.Currency.TRY);
        CurrencyMismatchException ex = assertThrows(
                CurrencyMismatchException.class,
                () -> transferDomainService.validateAndCreateTransfer(
                        participants, Money.of("100.00", Money.Currency.TRY)));
        assertEquals("Gönderici hesap para birimi (USD) ile transfer tutarı para birimi (TRY) eşleşmiyor.", ex.getMessage());
    }

    @Test
    void shouldThrowCurrencyMismatchExceptionWhenReceiverCurrencyMismatchesAmountCurrency() {
        TransferParticipants participants = new TransferParticipants(
            1L, "TR1", Money.Currency.TRY,
            2L, "TR2", Money.Currency.USD);
        CurrencyMismatchException ex = assertThrows(
                CurrencyMismatchException.class,
                () -> transferDomainService.validateAndCreateTransfer(
                        participants, Money.of("100.00", Money.Currency.TRY)));
        assertEquals("Alıcı hesap para birimi (USD) ile transfer tutarı para birimi (TRY) eşleşmiyor.", ex.getMessage());
    }
}
