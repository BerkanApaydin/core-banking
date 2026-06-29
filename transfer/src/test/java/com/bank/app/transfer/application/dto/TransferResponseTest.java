package com.bank.app.transfer.application.dto;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        TransferResponse resp = new TransferResponse(1L, "COMPLETED", BigDecimal.TEN, "TRY", now, "TR111", "TR222", 10L, 20L);
        assertEquals(1L, resp.id());
        assertEquals("COMPLETED", resp.status());
        assertEquals(BigDecimal.TEN, resp.amount());
        assertEquals("TRY", resp.currency());
        assertEquals(now, resp.createdAt());
        assertEquals("TR111", resp.senderIban());
        assertEquals("TR222", resp.receiverIban());
        assertEquals(10L, resp.senderAccountId());
        assertEquals(20L, resp.receiverAccountId());
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(NullPointerException.class,
                () -> new TransferResponse(null, "OK", BigDecimal.TEN, "TRY", LocalDateTime.now(), "TR111", "TR222", 10L, 20L));
    }

    @Test
    void shouldCreateFromTransferWithFromMethod() {
        Transfer transfer = new Transfer(1L, 10L, 20L, Money.of("100", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        TransferResponse resp = TransferResponse.from(transfer, "TR111", "TR222");
        assertEquals(1L, resp.id());
        assertEquals("COMPLETED", resp.status());
        assertEquals("100.00", resp.amount().toString());
        assertEquals("TRY", resp.currency());
    }
}
