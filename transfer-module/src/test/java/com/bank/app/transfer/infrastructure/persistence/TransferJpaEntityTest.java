package com.bank.app.transfer.infrastructure.persistence;

import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransferJpaEntityTest {

    @Test
    void shouldCreateTransferJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(1L, 2L, 3L, BigDecimal.TEN, "TRY", TransferStatus.PENDING, now);

        assertEquals(1L, entity.getId());
        assertEquals(2L, entity.getSenderAccountId());
        assertEquals(3L, entity.getReceiverAccountId());
        assertEquals(BigDecimal.TEN, entity.getAmount());
        assertEquals("TRY", entity.getCurrency());
        assertEquals(TransferStatus.PENDING, entity.getStatus());
        assertEquals(now, entity.getCreatedAt());

        TransferJpaEntity empty = new TransferJpaEntity();
        empty.setId(10L);
        empty.setSenderAccountId(20L);
        empty.setReceiverAccountId(30L);
        empty.setAmount(BigDecimal.ONE);
        empty.setCurrency("USD");
        empty.setStatus(TransferStatus.COMPLETED);
        empty.setCreatedAt(now);

        assertEquals(10L, empty.getId());
        assertEquals(20L, empty.getSenderAccountId());
        assertEquals(30L, empty.getReceiverAccountId());
        assertEquals(BigDecimal.ONE, empty.getAmount());
        assertEquals("USD", empty.getCurrency());
        assertEquals(TransferStatus.COMPLETED, empty.getStatus());
        assertEquals(now, empty.getCreatedAt());
    }
}
