package com.bank.app.transfer.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferJpaEntityTest {

    @Test
    void shouldCreateTransferJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(1L, 2L, 3L, BigDecimal.TEN, "TRY", "PENDING", null);

        assertEquals(1L, entity.getId());
        assertEquals(2L, entity.getSenderAccountId());
        assertEquals(3L, entity.getReceiverAccountId());
        assertEquals(BigDecimal.TEN, entity.getAmount());
        assertEquals("TRY", entity.getCurrency());
        assertEquals("PENDING", entity.getStatus());

        TransferJpaEntity empty = new TransferJpaEntity();
        empty.setId(10L);
        empty.setSenderAccountId(20L);
        empty.setReceiverAccountId(30L);
        empty.setAmount(BigDecimal.ONE);
        empty.setCurrency("USD");
        empty.setStatus("COMPLETED");
        empty.setVersion(5L);

        assertEquals(10L, empty.getId());
        assertEquals(20L, empty.getSenderAccountId());
        assertEquals(30L, empty.getReceiverAccountId());
        assertEquals(BigDecimal.ONE, empty.getAmount());
        assertEquals("USD", empty.getCurrency());
        assertEquals("COMPLETED", empty.getStatus());
        assertEquals(5L, empty.getVersion());
    }
}
