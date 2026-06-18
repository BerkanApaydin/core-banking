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
    void shouldMapDomainToJpaEntity() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domain = new Transfer(1L, 10L, 20L,
                Money.of("200.00", Money.Currency.TRY),
                TransferStatus.COMPLETED, now, 3L);

        TransferJpaEntity entity = mapper.toJpaEntity(domain);

        assertNotNull(entity);
        assertEquals(1L, entity.getId());
        assertEquals(10L, entity.getSenderAccountId());
        assertEquals(20L, entity.getReceiverAccountId());
        assertEquals(new BigDecimal("200.00"), entity.getAmount());
        assertEquals("TRY", entity.getCurrency());
        assertEquals("COMPLETED", entity.getStatus());
        assertEquals(now, entity.getCreatedAt());
        assertEquals(3L, entity.getVersion());
    }

    @Test
    void shouldMapJpaEntityToDomain() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(2L, 30L, 40L,
                new BigDecimal("150.50"), "USD", "PENDING", now);
        entity.setVersion(7L);

        Transfer domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(2L, domain.getId());
        assertEquals(30L, domain.getSenderAccountId());
        assertEquals(40L, domain.getReceiverAccountId());
        assertEquals(new BigDecimal("150.50"), domain.getAmount().amount());
        assertEquals(Money.Currency.USD, domain.getAmount().currency());
        assertEquals(TransferStatus.PENDING, domain.getStatus());
        assertEquals(now, domain.getCreatedAt());
        assertEquals(7L, domain.getVersion());
    }

    @Test
    void shouldReturnNullWhenDomainIsNull() {
        assertNull(mapper.toJpaEntity(null));
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(mapper.toDomain(null));
    }

    @Test
    void shouldMapDomainWithoutVersion() {
        LocalDateTime now = LocalDateTime.now();
        Transfer domain = new Transfer(3L, 50L, 60L,
                Money.of("100.00", Money.Currency.TRY),
                TransferStatus.PENDING, now);

        TransferJpaEntity entity = mapper.toJpaEntity(domain);

        assertNull(entity.getVersion());
    }

    @Test
    void shouldMapEntityWithoutVersion() {
        LocalDateTime now = LocalDateTime.now();
        TransferJpaEntity entity = new TransferJpaEntity(4L, 70L, 80L,
                new BigDecimal("99.99"), "EUR", "COMPLETED", now);

        Transfer domain = mapper.toDomain(entity);

        assertNull(domain.getVersion());
    }
}
