package com.bank.app.account.adapter.out.persistence;

import com.bank.app.account.domain.LedgerDirection;
import com.bank.app.account.domain.LedgerEntry;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the append-only journal adapter.
 *
 * <p>PIT reported {@code save} as NO_COVERAGE: the only callers are the
 * {@code AdjustAccountBalancesUseCaseImpl} tests behind mocks, so the adapter
 * itself never executes under mutation.
 */
@ExtendWith(MockitoExtension.class)
class LedgerPersistenceAdapterTest {

    @Mock private LedgerEntryJpaRepository repository;
    @Mock private LedgerJpaMapper mapper;

    @Test
    void shouldRejectNullEntry() {
        var adapter = new LedgerPersistenceAdapter(repository, mapper);

        assertThatThrownBy(() -> adapter.save(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ledger entry must not be null");
    }

    @Test
    void shouldRoundTripEntryThroughMapper() {
        var adapter = new LedgerPersistenceAdapter(repository, new LedgerJpaMapper());
        Money amount = Money.of("200.00", Currency.TRY);
        Money balance = Money.of("800.00", Currency.TRY);
        LedgerEntry entry = LedgerEntry.debit(1L, amount, balance, "ref-1", Clock.systemUTC());
        LedgerEntryJpaEntity persisted = new LedgerEntryJpaEntity();
        persisted.setId(5L);
        persisted.setTransactionRef("ref-1");
        persisted.setAccountId(1L);
        persisted.setDirection(LedgerDirection.DEBIT);
        persisted.setAmount(new BigDecimal("200.00"));
        persisted.setCurrency(Currency.TRY);
        persisted.setBalanceAfter(new BigDecimal("800.00"));
        persisted.setBusinessAt(LocalDateTime.now());
        when(repository.save(any())).thenReturn(persisted);

        LedgerEntry result = adapter.save(entry);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTransactionRef()).isEqualTo("ref-1");
        assertThat(result.getDirection()).isEqualTo(LedgerDirection.DEBIT);
        verify(repository).save(any());
    }
}
