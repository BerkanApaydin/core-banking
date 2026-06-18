package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Money;
import com.bank.app.common.exception.CurrencyMismatchException;
import com.bank.app.common.exception.SameAccountTransferException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransferDomainServiceTest {

        private final TransferDomainService transferDomainService = new TransferDomainService();

        @Test
        void shouldCreateTransferSuccessfully() {
                Transfer transfer = transferDomainService.execute(
                                1L,
                                "TR290006200000000000000111",
                                Money.Currency.TRY,
                                2L,
                                "TR290006200000000000000222",
                                Money.Currency.TRY,
                                Money.of("300.00", Money.Currency.TRY));

                assertNotNull(transfer);
                assertEquals(1L, transfer.getSenderAccountId());
                assertEquals(2L, transfer.getReceiverAccountId());
                assertEquals(TransferStatus.PENDING, transfer.getStatus());
        }

        @Test
        void shouldThrowSameAccountTransferExceptionWhenIdsAreEqual() {
                SameAccountTransferException ex = assertThrows(
                                SameAccountTransferException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                Money.Currency.TRY,
                                                1L,
                                                "TR2",
                                                Money.Currency.TRY,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertEquals("Aynı hesaba transfer yapılamaz: TR1", ex.getMessage());
        }

        @Test
        void shouldThrowSameAccountTransferExceptionWhenIbansAreEqualIgnoringCase() {
                SameAccountTransferException ex = assertThrows(
                                SameAccountTransferException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "tr123",
                                                Money.Currency.TRY,
                                                2L,
                                                "TR123",
                                                Money.Currency.TRY,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertEquals("Aynı hesaba transfer yapılamaz: tr123", ex.getMessage());
        }

        @Test
        void shouldThrowNullPointerExceptionWhenAmountIsNull() {
                NullPointerException ex = assertThrows(
                                NullPointerException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                Money.Currency.TRY,
                                                2L,
                                                "TR2",
                                                Money.Currency.TRY,
                                                null));

                assertEquals("Transfer tutarı null olamaz", ex.getMessage());
        }

        @Test
        void shouldThrowNullPointerExceptionWhenSenderIbanIsNull() {
                NullPointerException ex = assertThrows(
                                NullPointerException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                null,
                                                Money.Currency.TRY,
                                                2L,
                                                "TR2",
                                                Money.Currency.TRY,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertEquals("Gönderici IBAN null olamaz", ex.getMessage());
        }

        @Test
        void shouldThrowNullPointerExceptionWhenReceiverIbanIsNull() {
                NullPointerException ex = assertThrows(
                                NullPointerException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                Money.Currency.TRY,
                                                2L,
                                                null,
                                                Money.Currency.TRY,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertEquals("Alıcı IBAN null olamaz", ex.getMessage());
        }

        @Test
        void shouldThrowNullPointerExceptionWhenSenderCurrencyIsNull() {
                NullPointerException ex = assertThrows(
                                NullPointerException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                null,
                                                2L,
                                                "TR2",
                                                Money.Currency.TRY,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertEquals("Gönderici para birimi null olamaz", ex.getMessage());
        }

        @Test
        void shouldThrowNullPointerExceptionWhenReceiverCurrencyIsNull() {
                NullPointerException ex = assertThrows(
                                NullPointerException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                Money.Currency.TRY,
                                                2L,
                                                "TR2",
                                                null,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertEquals("Alıcı para birimi null olamaz", ex.getMessage());
        }

        @Test
        void shouldThrowCurrencyMismatchExceptionWhenSenderCurrencyMismatchesAmountCurrency() {
                CurrencyMismatchException ex = assertThrows(
                                CurrencyMismatchException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                Money.Currency.USD,
                                                2L,
                                                "TR2",
                                                Money.Currency.TRY,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertTrue(ex.getMessage().contains("Gönderici hesap para birimi (USD) ile transfer tutarı para birimi (TRY) eşleşmiyor"));
        }

        @Test
        void shouldThrowCurrencyMismatchExceptionWhenReceiverCurrencyMismatchesAmountCurrency() {
                CurrencyMismatchException ex = assertThrows(
                                CurrencyMismatchException.class,
                                () -> transferDomainService.execute(
                                                1L,
                                                "TR1",
                                                Money.Currency.TRY,
                                                2L,
                                                "TR2",
                                                Money.Currency.USD,
                                                Money.of("100.00", Money.Currency.TRY)));

                assertTrue(ex.getMessage().contains("Alıcı hesap para birimi (USD) ile transfer tutarı para birimi (TRY) eşleşmiyor"));
        }
}