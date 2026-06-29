package com.bank.app.transfer.application.dto;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferDetailResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        TransferDetailResponse resp = new TransferDetailResponse(1L, 10L, 20L, Money.of("100", Currency.TRY).amount(), "TRY", "COMPLETED", now);
        assertEquals(1L, resp.id());
        assertEquals(10L, resp.senderAccountId());
        assertEquals(20L, resp.receiverAccountId());
        assertEquals("TRY", resp.currency());
        assertEquals("COMPLETED", resp.status());
        assertEquals(now, resp.createdAt());
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(NullPointerException.class,
                () -> new TransferDetailResponse(null, 10L, 20L, Money.of("100", Currency.TRY).amount(), "TRY", "OK", LocalDateTime.now()));
    }

    @Test
    void shouldCreateFromTransfer() {
        Transfer transfer = new Transfer(1L, 10L, 20L, Money.of("100", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        TransferDetailResponse resp = TransferDetailResponse.from(transfer);
        assertEquals(1L, resp.id());
        assertEquals("COMPLETED", resp.status());
    }
}
