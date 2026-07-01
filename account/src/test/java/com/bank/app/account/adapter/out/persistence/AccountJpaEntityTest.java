package com.bank.app.account.adapter.out.persistence;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("null")
class AccountJpaEntityTest {

    @Test
    void shouldCreateAccountJpaEntity() {
        AccountJpaEntity entity = new AccountJpaEntity(1L, 2L, "IBAN", "name", BigDecimal.TEN, "TRY", "ACTIVE", null);

        assertThat(entity.getId()).isEqualTo(1L);
        assertThat(entity.getUserId()).isEqualTo(2L);
        assertThat(entity.getIban()).isEqualTo("IBAN");
        assertThat(entity.getOwnerName()).isEqualTo("name");
        assertThat(entity.getBalance()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(entity.getCurrency()).isEqualTo("TRY");
        assertThat(entity.getStatus()).isEqualTo("ACTIVE");

        AccountJpaEntity empty = new AccountJpaEntity();
        empty.setId(10L);
        empty.setUserId(20L);
        empty.setIban("IBAN2");
        empty.setOwnerName("name2");
        empty.setBalance(BigDecimal.ONE);
        empty.setCurrency("USD");
        empty.setStatus("SUSPENDED");
        empty.setVersion(5L);

        assertThat(empty.getId()).isEqualTo(10L);
        assertThat(empty.getUserId()).isEqualTo(20L);
        assertThat(empty.getIban()).isEqualTo("IBAN2");
        assertThat(empty.getOwnerName()).isEqualTo("name2");
        assertThat(empty.getBalance()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(empty.getCurrency()).isEqualTo("USD");
        assertThat(empty.getStatus()).isEqualTo("SUSPENDED");
        assertThat(empty.getVersion()).isEqualTo(5L);
    }
}
