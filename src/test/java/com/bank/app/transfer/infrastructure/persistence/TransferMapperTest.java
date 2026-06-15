package com.bank.app.transfer.infrastructure.persistence;

import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TransferMapperTest {

    private final TransferMapper mapper = new TransferMapper();

    @Test
    void shouldMapJpaEntityToDomain() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(10L, 1L, 2L, new BigDecimal("250.00"), "TRY", "COMPLETED", now);
        
        Transfer domain = mapper.toDomain(entity);
        
        assertNotNull(domain);
        assertEquals(10L, domain.getId());
        assertEquals(1L, domain.getSenderAccountId());
        assertEquals(2L, domain.getReceiverAccountId());
        assertEquals(new BigDecimal("250.00"), domain.getAmount().amount());
        assertEquals(Money.Currency.TRY, domain.getAmount().currency());
        assertEquals(TransferStatus.COMPLETED, domain.getStatus());
        assertEquals(now, domain.getCreatedAt());
    }

    @Test
    void shouldMapDomainToJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domain = new Transfer(10L, 1L, 2L, Money.of("250.00", Money.Currency.TRY), TransferStatus.COMPLETED, now);
        
        TransferJpaEntity entity = mapper.toJpaEntity(domain);
        
        assertNotNull(entity);
        assertEquals(10L, entity.getId());
        assertEquals(1L, entity.getSenderAccountId());
        assertEquals(2L, entity.getReceiverAccountId());
        assertEquals(new BigDecimal("250.00"), entity.getAmount());
        assertEquals("TRY", entity.getCurrency());
        assertEquals("COMPLETED", entity.getStatus());
        assertEquals(now, entity.getCreatedAt());
    }

    @Test
    void shouldReturnNullWhenMappingNullValues() {
        assertNull(mapper.toDomain(null));
        assertNull(mapper.toJpaEntity(null));
    }
}
