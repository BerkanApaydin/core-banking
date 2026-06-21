package com.bank.app.account.infrastructure.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountJpaEntityTest {

    @Test
    void shouldCreateAccountJpaEntity() {
        AccountJpaEntity entity = new AccountJpaEntity(1L, 2L, "IBAN", "name", BigDecimal.TEN, "TRY", "ACTIVE");

        assertEquals(1L, entity.getId());
        assertEquals(2L, entity.getUserId());
        assertEquals("IBAN", entity.getIban());
        assertEquals("name", entity.getOwnerName());
        assertEquals(BigDecimal.TEN, entity.getBalance());
        assertEquals("TRY", entity.getCurrency());
        assertEquals("ACTIVE", entity.getStatus());

        AccountJpaEntity empty = new AccountJpaEntity();
        empty.setId(10L);
        empty.setUserId(20L);
        empty.setIban("IBAN2");
        empty.setOwnerName("name2");
        empty.setBalance(BigDecimal.ONE);
        empty.setCurrency("USD");
        empty.setStatus("SUSPENDED");
        empty.setVersion(5L);

        assertEquals(10L, empty.getId());
        assertEquals(20L, empty.getUserId());
        assertEquals("IBAN2", empty.getIban());
        assertEquals("name2", empty.getOwnerName());
        assertEquals(BigDecimal.ONE, empty.getBalance());
        assertEquals("USD", empty.getCurrency());
        assertEquals("SUSPENDED", empty.getStatus());
        assertEquals(5L, empty.getVersion());
    }
}
