package com.bank.app.account.domain;

import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Aggregate-contract coverage for the account domain events.
 *
 * <p>PIT reported every {@code aggregateType}/{@code aggregateId} override as
 * NO_COVERAGE: the per-event tests assert payload fields but never the
 * {@code DomainEvent} routing contract. One contract test per event kills both
 * EmptyObject mutants ("" vs "Account" / "" vs id).
 */
class AccountEventsAggregateContractTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 10, 30);

    @Test
    void createdEventShouldExposeAggregateRouting() {
        var event = new AccountCreatedEvent(7L, new UserId(10L),
                new Iban("TR440006200000000000000123"), "Owner",
                Money.of("100.00", Currency.TRY), NOW);
        assertThat(event.aggregateType()).isEqualTo("Account");
        assertThat(event.aggregateId()).isEqualTo("7");
    }

    @Test
    void creditedEventShouldExposeAggregateRouting() {
        var event = new AccountCreditedEvent(8L, Money.of("100.00", Currency.TRY),
                Money.of("500.00", Currency.TRY), NOW);
        assertThat(event.aggregateType()).isEqualTo("Account");
        assertThat(event.aggregateId()).isEqualTo("8");
    }

    @Test
    void debitedEventShouldExposeAggregateRouting() {
        var event = new AccountDebitedEvent(9L, Money.of("100.00", Currency.TRY),
                Money.of("400.00", Currency.TRY), NOW);
        assertThat(event.aggregateType()).isEqualTo("Account");
        assertThat(event.aggregateId()).isEqualTo("9");
    }

    @Test
    void closedEventShouldExposeAggregateRouting() {
        var event = new AccountClosedEvent(10L, Money.of("0.00", Currency.TRY), NOW);
        assertThat(event.aggregateType()).isEqualTo("Account");
        assertThat(event.aggregateId()).isEqualTo("10");
    }

    @Test
    void suspendedEventShouldExposeAggregateRouting() {
        var event = new AccountSuspendedEvent(11L, NOW);
        assertThat(event.aggregateType()).isEqualTo("Account");
        assertThat(event.aggregateId()).isEqualTo("11");
    }
}
