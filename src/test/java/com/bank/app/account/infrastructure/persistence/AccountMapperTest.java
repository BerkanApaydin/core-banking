package com.bank.app.account.infrastructure.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    @Test
    void shouldMapJpaEntityToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(1L, 100L, "TR290006200000000000000111", "Ahmet", new BigDecimal("1000.00"), "TRY", true);
        
        Account domain = mapper.toDomain(entity);
        
        assertNotNull(domain);
        assertEquals(1L, domain.getId());
        assertEquals(100L, domain.getUserId());
        assertEquals("TR290006200000000000000111", domain.getIban().value());
        assertEquals("Ahmet", domain.getOwnerName());
        assertEquals(new BigDecimal("1000.00"), domain.getBalance().amount());
        assertEquals(Money.Currency.TRY, domain.getBalance().currency());
        assertTrue(domain.isActive());
    }

    @Test
    void shouldMapDomainToJpaEntity() {
        Account domain = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Ahmet", Money.of("1000.00", Money.Currency.TRY), true);
        
        AccountJpaEntity entity = mapper.toJpaEntity(domain);
        
        assertNotNull(entity);
        assertEquals(1L, entity.getId());
        assertEquals(100L, entity.getUserId());
        assertEquals("TR290006200000000000000111", entity.getIban());
        assertEquals("Ahmet", entity.getOwnerName());
        assertEquals(new BigDecimal("1000.00"), entity.getBalance());
        assertEquals("TRY", entity.getCurrency());
        assertTrue(entity.isActive());
    }

    @Test
    void shouldReturnNullWhenMappingNullValues() {
        assertNull(mapper.toDomain(null));
        assertNull(mapper.toJpaEntity(null));
    }
}
