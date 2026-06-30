package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("null")
class AccountMapperTest {

    private final AccountJpaMapper mapper = new AccountJpaMapper();

    @Test
    void shouldMapDomainToJpaEntity() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domain = new Account(1L, new UserId(100L), iban, "Ahmet",
                Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE, 3L);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertNotNull(entity);
        assertEquals(1L, entity.getId());
        assertEquals(100L, entity.getUserId());
        assertEquals("TR290006200000000000000111", entity.getIban());
        assertEquals("Ahmet", entity.getOwnerName());
        assertEquals(new BigDecimal("1000.00"), entity.getBalance());
        assertEquals("TRY", entity.getCurrency());
        assertEquals("ACTIVE", entity.getStatus());
        assertEquals(3L, entity.getVersion());
    }

    @Test
    void shouldMapJpaEntityToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222",
                "Mehmet", new BigDecimal("500.00"), "USD", "SUSPENDED", 5L);

        Account domain = mapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals(2L, domain.getId());
        assertEquals(200L, domain.getUserId().value());
        assertEquals("TR290006200000000000000222", domain.getIban().value());
        assertEquals("Mehmet", domain.getOwnerName());
        assertEquals(new BigDecimal("500.00"), domain.getBalance().amount());
        assertEquals(Currency.USD, domain.getBalance().currency());
        assertEquals(AccountStatus.SUSPENDED, domain.getStatus());
        assertFalse(domain.isActive());
        assertEquals(5L, domain.getVersion());
    }

    @Test
    void shouldThrowWhenDomainIsNull() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toJpaEntity(null));
    }

    @Test
    void shouldThrowWhenEntityIsNull() {
        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(null));
    }

    @Test
    void shouldMapDomainWithoutVersion() {
        Iban iban = new Iban("TR290006200000000000000333");
        Account domain = new Account(3L, new UserId(300L), iban, "Ali",
                Money.of("750.00", Currency.TRY), AccountStatus.ACTIVE);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertNull(entity.getVersion());
    }

    @Test
    void shouldMapEntityWithoutVersion() {
        AccountJpaEntity entity = new AccountJpaEntity(4L, 400L, "TR290006200000000000000444",
                "Veli", new BigDecimal("300.00"), "TRY", "ACTIVE", null);

        Account domain = mapper.toDomain(entity);

        assertNull(domain.getVersion());
    }

    @Test
    void shouldMapDomainToJpaEntityWithEurCurrency() {
        Iban iban = new Iban("TR290006200000000000000555");
        Account domain = new Account(5L, new UserId(500L), iban, "Ayse",
                Money.of("2000.00", Currency.EUR), AccountStatus.ACTIVE);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertEquals("EUR", entity.getCurrency());
    }

    @Test
    void shouldMapJpaEntityWithEurToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(6L, 600L, "TR290006200000000000000666",
                "Fatma", new BigDecimal("1500.00"), "EUR", "ACTIVE", null);

        Account domain = mapper.toDomain(entity);

        assertEquals(Currency.EUR, domain.getBalance().currency());
    }

    @Test
    void shouldMapJpaEntityWithClosedStatusToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(7L, 700L, "TR290006200000000000000777",
                "Zeynep", new BigDecimal("0.00"), "TRY", "CLOSED", null);

        Account domain = mapper.toDomain(entity);

        assertEquals(AccountStatus.CLOSED, domain.getStatus());
        assertFalse(domain.isActive());
    }

    @Test
    void shouldMapDomainWithClosedStatusToJpaEntity() {
        Iban iban = new Iban("TR290006200000000000000888");
        Account domain = new Account(8L, new UserId(800L), iban, "Bora",
                Money.of("0.00", Currency.TRY), AccountStatus.CLOSED);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertEquals("CLOSED", entity.getStatus());
    }

    @Test
    void shouldThrowWhenEntityHasInvalidEnumValue() {
        AccountJpaEntity entity = new AccountJpaEntity(9L, 900L, "TR290006200000000000000999",
                "Invalid", new BigDecimal("100.00"), "INVALID", "ACTIVE", null);

        assertThrows(IllegalArgumentException.class, () -> mapper.toDomain(entity));
    }
}
