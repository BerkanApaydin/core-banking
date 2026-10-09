package com.bank.app.transfer.application.dto;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import com.bank.app.common.domain.AccountId;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferDetailResponseTest {

    @Test
    void shouldCreateWithAllFields() {
        LocalDateTime now = LocalDateTime.now();
        TransferDetailResponse resp = new TransferDetailResponse(1L, 10L, 20L, Money.of("100", Currency.TRY).amount(), "TRY", TransferStatus.COMPLETED, now, 5L);
        assertEquals(1L, resp.id());
        assertEquals(10L, resp.senderAccountId());
        assertEquals(20L, resp.receiverAccountId());
        assertEquals("TRY", resp.currency());
        assertEquals(TransferStatus.COMPLETED, resp.status());
        assertEquals(now, resp.createdAt());
        assertEquals(5L, resp.version());
    }

    @Test
    void shouldRejectNullId() {
        assertThrows(NullPointerException.class,
                () -> new TransferDetailResponse(null, 10L, 20L, Money.of("100", Currency.TRY).amount(), "TRY", TransferStatus.COMPLETED, LocalDateTime.now(), null));
    }

    @Test
    void shouldCreateFromTransfer() {
        Transfer transfer = new Transfer(1L, new AccountId(10L), new AccountId(20L), Money.of("100", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now());
        TransferDetailResponse resp = TransferDetailResponse.from(transfer);
        assertEquals(1L, resp.id());
        assertEquals(TransferStatus.COMPLETED, resp.status());
        // I-10: version flows to the wire so clients can use If-Match/ETag.
        assertNull(resp.version());
    }

    @Test
    void shouldCarryVersionFromTransfer() {
        Transfer transfer = new Transfer(1L, new AccountId(10L), new AccountId(20L), Money.of("100", Currency.TRY), TransferStatus.COMPLETED, LocalDateTime.now(), 7L);
        TransferDetailResponse resp = TransferDetailResponse.from(transfer);
        assertEquals(7L, resp.version());
    }
}
