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
    void shouldMapDomainToJpaEntity() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domain = new Account(1L, 100L, iban, "Ahmet",
                Money.of("1000.00", Money.Currency.TRY), true, 3L);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertNotNull(entity);
        assertEquals(1L, entity.getId());
        assertEquals(100L, entity.getUserId());
        assertEquals("TR290006200000000000000111", entity.getIban());
        assertEquals("Ahmet", entity.getOwnerName());
        assertEquals(new BigDecimal("1000.00"), entity.getBalance());
        assertEquals("TRY", entity.getCurrency());
        assertTrue(entity.isActive());
        assertEquals(3L, entity.getVersion());
    }

    @Test
    void shouldMapJpaEntityToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222",
                "Mehmet", new BigDecimal("500.00"), "USD", false, 5L);

        Account domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(2L, domain.getId());
        assertEquals(200L, domain.getUserId());
        assertEquals("TR290006200000000000000222", domain.getIban().value());
        assertEquals("Mehmet", domain.getOwnerName());
        assertEquals(new BigDecimal("500.00"), domain.getBalance().amount());
        assertEquals(Money.Currency.USD, domain.getBalance().currency());
        assertFalse(domain.isActive());
        assertEquals(5L, domain.getVersion());
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
        Iban iban = new Iban("TR290006200000000000000333");
        Account domain = new Account(3L, 300L, iban, "Ali",
                Money.of("750.00", Money.Currency.TRY), true);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertNull(entity.getVersion());
    }

    @Test
    void shouldMapEntityWithoutVersion() {
        AccountJpaEntity entity = new AccountJpaEntity(4L, 400L, "TR290006200000000000000444",
                "Veli", new BigDecimal("300.00"), "TRY", true);

        Account domain = mapper.toDomain(entity);

        assertNull(domain.getVersion());
    }
}
