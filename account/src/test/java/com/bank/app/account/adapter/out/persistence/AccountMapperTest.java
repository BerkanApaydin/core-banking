package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("null")
class AccountMapperTest {

    private final AccountJpaMapper mapper = new AccountJpaMapper();

    @Test
    void shouldMapDomainToJpaEntity() {
        Iban iban = new Iban("TR290006200000000000000111");
        Account domain = new Account(1L, new UserId(100L), iban, "Ahmet",
                Money.of("1000.00", Currency.TRY), AccountStatus.ACTIVE, 3L);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(100L);
        assertThat(entity.getIban()).isEqualTo("TR290006200000000000000111");
        assertThat(entity.getOwnerName()).isEqualTo("Ahmet");
        assertThat(entity.getBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(entity.getCurrency()).isEqualTo("TRY");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");
        assertThat(entity.getVersion()).isEqualTo(3L);
    }

    @Test
    void shouldMapJpaEntityToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(2L, 200L, "TR290006200000000000000222",
                "Mehmet", new BigDecimal("500.00"), "USD", "SUSPENDED", 5L);

        Account domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo(2L);
        assertThat(domain.getUserId().value()).isEqualTo(200L);
        assertThat(domain.getIban().value()).isEqualTo("TR290006200000000000000222");
        assertThat(domain.getOwnerName()).isEqualTo("Mehmet");
        assertThat(domain.getBalance().amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(domain.getBalance().currency()).isEqualTo(Currency.USD);
        assertThat(domain.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(domain.isActive()).isFalse();
        assertThat(domain.getVersion()).isEqualTo(5L);
    }

    @Test
    void shouldThrowWhenDomainIsNull() {
        assertThatThrownBy(() -> mapper.toJpaEntity(null))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldThrowWhenEntityIsNull() {
        assertThatThrownBy(() -> mapper.toDomain(null))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMapDomainWithoutVersion() {
        Iban iban = new Iban("TR290006200000000000000333");
        Account domain = new Account(3L, new UserId(300L), iban, "Ali",
                Money.of("750.00", Currency.TRY), AccountStatus.ACTIVE);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertThat(entity.getVersion()).isNull();
    }

    @Test
    void shouldMapEntityWithoutVersion() {
        AccountJpaEntity entity = new AccountJpaEntity(4L, 400L, "TR290006200000000000000444",
                "Veli", new BigDecimal("300.00"), "TRY", "ACTIVE", null);

        Account domain = mapper.toDomain(entity);

        assertThat(domain.getVersion()).isNull();
    }

    @Test
    void shouldMapDomainToJpaEntityWithEurCurrency() {
        Iban iban = new Iban("TR290006200000000000000555");
        Account domain = new Account(5L, new UserId(500L), iban, "Ayse",
                Money.of("2000.00", Currency.EUR), AccountStatus.ACTIVE);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertThat(entity.getCurrency()).isEqualTo("EUR");
    }

    @Test
    void shouldMapJpaEntityWithEurToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(6L, 600L, "TR290006200000000000000666",
                "Fatma", new BigDecimal("1500.00"), "EUR", "ACTIVE", null);

        Account domain = mapper.toDomain(entity);

        assertThat(domain.getBalance().currency()).isEqualTo(Currency.EUR);
    }

    @Test
    void shouldMapJpaEntityWithClosedStatusToDomain() {
        AccountJpaEntity entity = new AccountJpaEntity(7L, 700L, "TR290006200000000000000777",
                "Zeynep", new BigDecimal("0.00"), "TRY", "CLOSED", null);

        Account domain = mapper.toDomain(entity);

        assertThat(domain.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(domain.isActive()).isFalse();
    }

    @Test
    void shouldMapDomainWithClosedStatusToJpaEntity() {
        Iban iban = new Iban("TR290006200000000000000888");
        Account domain = new Account(8L, new UserId(800L), iban, "Bora",
                Money.of("0.00", Currency.TRY), AccountStatus.CLOSED);

        AccountJpaEntity entity = mapper.toJpaEntity(domain);

        assertThat(entity.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void shouldThrowWhenEntityHasInvalidEnumValue() {
        AccountJpaEntity entity = new AccountJpaEntity(9L, 900L, "TR290006200000000000000999",
                "Invalid", new BigDecimal("100.00"), "INVALID", "ACTIVE", null);

        assertThatThrownBy(() -> mapper.toDomain(entity))
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
