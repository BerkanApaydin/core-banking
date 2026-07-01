package com.bank.app.infrastructure.adapter.out.outbox;

import com.bank.app.account.domain.AccountClosedEvent;
import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.account.domain.AccountCreditedEvent;
import com.bank.app.account.domain.AccountDebitedEvent;
import com.bank.app.account.domain.AccountSuspendedEvent;
import com.bank.app.common.application.port.out.OutboxPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.TransferStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventOutboxAdapterTest {

    @Mock
    private OutboxPort outboxPort;

    @Mock
    private ObjectMapper objectMapper;

    private DomainEventOutboxAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DomainEventOutboxAdapter(outboxPort, objectMapper);
    }

    @Test
    void publishShouldSaveAccountEvent() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publish(new AccountCreatedEvent(1L, new UserId(1L),
                new Iban("TR290006200000000000000123"), "owner",
                Money.of("1000.00", Currency.TRY), LocalDateTime.now()));

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort).save(captor.capture());

        OutboxPort.EventEntry entry = captor.getValue();
        assertThat(entry.aggregateType()).isEqualTo("Account");
        assertThat(entry.aggregateId()).isEqualTo("1");
        assertThat(entry.eventType()).isEqualTo("AccountCreatedEvent");
    }

    @Test
    void publishShouldSaveTransferEvent() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Money amount = Money.of("100.00", Currency.TRY);
        adapter.publish(new TransferCompletedEvent(99L, 2L, 3L, amount,
                TransferStatus.COMPLETED, LocalDateTime.now()));

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort).save(captor.capture());

        OutboxPort.EventEntry entry = captor.getValue();
        assertThat(entry.aggregateType()).isEqualTo("Transfer");
        assertThat(entry.aggregateId()).isEqualTo("99");
        assertThat(entry.eventType()).isEqualTo("TransferCompletedEvent");
    }

    @Test
    void publishShouldHandleAllAccountEventTypes() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publish(new AccountCreatedEvent(1L, new UserId(1L),
                new Iban("TR290006200000000000000123"), "owner",
                Money.of("100.00", Currency.TRY), LocalDateTime.now()));
        Money newBalance = Money.of("0.00", Currency.TRY);
        adapter.publish(new AccountDebitedEvent(2L, Money.of("50.00", Currency.TRY), newBalance, LocalDateTime.now()));
        adapter.publish(new AccountCreditedEvent(3L, Money.of("50.00", Currency.TRY), newBalance, LocalDateTime.now()));
        adapter.publish(new AccountClosedEvent(4L, newBalance, LocalDateTime.now()));
        adapter.publish(new AccountSuspendedEvent(5L, LocalDateTime.now()));

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort, org.mockito.Mockito.times(5)).save(captor.capture());

        assertThat(captor.getAllValues()).allMatch(e -> e.aggregateType().equals("Account"));
    }

    @Test
    void publishShouldHandleAllTransferEventTypes() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Money amount = Money.of("100.00", Currency.TRY);
        adapter.publish(new TransferCompletedEvent(1L, 2L, 3L, amount,
                TransferStatus.COMPLETED, LocalDateTime.now()));
        adapter.publish(new TransferCancelledEvent(2L, 2L, 3L, amount,
                TransferStatus.CANCELLED, LocalDateTime.now()));

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort, org.mockito.Mockito.times(2)).save(captor.capture());

        assertThat(captor.getAllValues()).allMatch(e -> e.aggregateType().equals("Transfer"));
    }

    @Test
    void publishShouldUseSerializedPayload() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"serialized\":true}");

        adapter.publish(new AccountCreatedEvent(1L, new UserId(1L),
                new Iban("TR290006200000000000000123"), "owner",
                Money.of("1000.00", Currency.TRY), LocalDateTime.now()));

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort).save(captor.capture());
        assertThat(captor.getValue().payload()).isEqualTo("{\"serialized\":true}");
    }

    @Test
    void publishShouldSetCorrectDefaultFields() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publish(new AccountCreatedEvent(1L, new UserId(1L),
                new Iban("TR290006200000000000000123"), "owner",
                Money.of("1000.00", Currency.TRY), LocalDateTime.now()));

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort).save(captor.capture());

        OutboxPort.EventEntry entry = captor.getValue();
        assertThat(entry.retryCount()).isZero();
        assertThat(entry.processed()).isFalse();
        assertThat(entry.deadLetter()).isFalse();
        assertThat(entry.lastError()).isNull();
        assertThat(entry.partition()).isBetween(0, 15);
        assertThat(entry.createdAt()).isNotNull();
        assertThat(entry.id()).isNotNull();
    }

    @Test
    void publishShouldHandleUnknownEventType() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        adapter.publish(() -> LocalDateTime.now());

        ArgumentCaptor<OutboxPort.EventEntry> captor = ArgumentCaptor.captor();
        verify(outboxPort).save(captor.capture());

        OutboxPort.EventEntry entry = captor.getValue();
        assertThat(entry.aggregateId()).isEqualTo("unknown");
        assertThat(entry.partition()).isBetween(0, 15);
    }
}
