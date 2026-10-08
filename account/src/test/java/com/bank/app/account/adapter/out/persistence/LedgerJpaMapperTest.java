package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.LedgerDirection;
import com.bank.app.account.domain.LedgerEntry;
import com.bank.app.account.domain.TransactionRef;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerJpaMapperTest {

    private final LedgerJpaMapper mapper = new LedgerJpaMapper();

    private LedgerEntry entry() {
        return new LedgerEntry(42L, new TransactionRef("ref-1"), 7L, LedgerDirection.DEBIT,
                Money.of("200.00", Currency.TRY), Money.of("800.00", Currency.TRY),
                LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    @Test
    void shouldRoundTrip() {
        LedgerEntryJpaEntity entity = mapper.toJpaEntity(entry());

        assertThat(entity.getTransactionRef()).isEqualTo("ref-1");
        assertThat(entity.getAccountId()).isEqualTo(7L);
        assertThat(entity.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(entity.getCurrency()).isEqualTo(Currency.TRY);

        LedgerEntry back = mapper.toDomain(entity);

        assertThat(back.getId()).isEqualTo(42L);
        assertThat(back.getTransactionRef()).isEqualTo(new TransactionRef("ref-1"));
        assertThat(back.getAccountId()).isEqualTo(7L);
        assertThat(back.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        assertThat(back.getAmount()).isEqualTo(Money.of("200.00", Currency.TRY));
        assertThat(back.getBalanceAfter()).isEqualTo(Money.of("800.00", Currency.TRY));
        assertThat(back.getOccurredAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    @Test
    void shouldRejectNulls() {
        assertThatThrownBy(() -> mapper.toJpaEntity(null)).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toDomain(null)).isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
