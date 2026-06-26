package com.bank.app.transfer.application.dto;

import com.bank.app.common.domain.Currency;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferRequestTest {

    @Test
    void shouldCreateWithAllFields() {
        TransferRequest req = new TransferRequest("TR111", "TR222", BigDecimal.TEN, Currency.TRY);
        assertEquals("TR111", req.senderIban());
        assertEquals("TR222", req.receiverIban());
        assertEquals(BigDecimal.TEN, req.amount());
        assertEquals(Currency.TRY, req.currency());
    }

    @Test
    void shouldRejectNullSenderIban() {
        assertThrows(NullPointerException.class,
                () -> new TransferRequest(null, "TR222", BigDecimal.TEN, Currency.TRY));
    }

    @Test
    void shouldRejectNullReceiverIban() {
        assertThrows(NullPointerException.class,
                () -> new TransferRequest("TR111", null, BigDecimal.TEN, Currency.TRY));
    }

    @Test
    void shouldRejectNullAmount() {
        assertThrows(NullPointerException.class,
                () -> new TransferRequest("TR111", "TR222", null, Currency.TRY));
    }

    @Test
    void shouldRejectNullCurrency() {
        assertThrows(NullPointerException.class,
                () -> new TransferRequest("TR111", "TR222", BigDecimal.TEN, null));
    }
}
