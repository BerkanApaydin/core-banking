package com.bank.app.audit.infrastructure.event;

import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.Money;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.transfer.domain.TransferCancelledEvent;
import com.bank.app.transfer.domain.TransferCompletedEvent;
import com.bank.app.transfer.domain.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {

    @Mock
    private AuditLogger auditLogger;

    private AuditEventListener eventListener;

    @BeforeEach
    void setUp() {
        eventListener = new AuditEventListener(auditLogger);
    }

    @Test
    void shouldLogWhenAccountCreated() {
        Money balance = Money.of("1000.00", Money.Currency.TRY);
        AccountCreatedEvent event = new AccountCreatedEvent(1L, 10L, "TR123456", "Test User", balance);

        eventListener.onAccountCreated(event);

        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CREATED), anyString());
    }

    @Test
    void shouldLogWhenTransferCompleted() {
        Money amount = Money.of("500.00", Money.Currency.TRY);
        Transfer transfer = new Transfer(1L, 10L, 20L, amount, TransferStatus.COMPLETED, LocalDateTime.now());
        TransferCompletedEvent event = new TransferCompletedEvent(transfer);

        eventListener.onTransferCompleted(event);

        verify(auditLogger).log(eq(AuditAction.TRANSFER_EXECUTED), anyString());
    }

    @Test
    void shouldLogWhenTransferCancelled() {
        Money amount = Money.of("200.00", Money.Currency.TRY);
        TransferCancelledEvent event = new TransferCancelledEvent(1L, 10L, 20L, amount);

        eventListener.onTransferCancelled(event);

        verify(auditLogger).log(eq(AuditAction.TRANSFER_CANCELLED), anyString());
    }
}
