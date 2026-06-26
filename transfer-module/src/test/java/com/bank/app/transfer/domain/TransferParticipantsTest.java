package com.bank.app.transfer.domain;

import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferParticipantsTest {

    @Test
    void shouldCreateWithValidFields() {
        TransferParticipants p = new TransferParticipants(1L, "TR290006200000000000000001", Currency.TRY,
                2L, "TR290006200000000000000002", Currency.TRY);
        assertEquals(1L, p.senderId());
        assertEquals("TR290006200000000000000001", p.senderIban());
        assertEquals(Currency.TRY, p.senderCurrency());
        assertEquals(2L, p.receiverId());
        assertEquals("TR290006200000000000000002", p.receiverIban());
        assertEquals(Currency.TRY, p.receiverCurrency());
    }

    @Test
    void shouldThrowWhenSenderIdIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferParticipants(null, "iban", Currency.TRY,
                2L, "iban2", Currency.TRY));
    }

    @Test
    void shouldThrowWhenSenderIbanIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferParticipants(1L, null, Currency.TRY,
                2L, "iban2", Currency.TRY));
    }

    @Test
    void shouldThrowWhenSenderCurrencyIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferParticipants(1L, "iban", null,
                2L, "iban2", Currency.TRY));
    }

    @Test
    void shouldThrowWhenReceiverIdIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferParticipants(1L, "iban", Currency.TRY,
                null, "iban2", Currency.TRY));
    }

    @Test
    void shouldThrowWhenReceiverIbanIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferParticipants(1L, "iban", Currency.TRY,
                2L, null, Currency.TRY));
    }

    @Test
    void shouldThrowWhenReceiverCurrencyIsNull() {
        assertThrows(NullPointerException.class, () -> new TransferParticipants(1L, "iban", Currency.TRY,
                2L, "iban2", null));
    }
}
