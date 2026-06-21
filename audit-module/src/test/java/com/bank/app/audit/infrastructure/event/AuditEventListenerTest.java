package com.bank.app.audit.infrastructure.event;

import com.bank.app.account.domain.AccountCreatedEvent;
import com.bank.app.audit.application.AuditLogger;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.common.domain.Money;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.times;

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

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger).log(eq(AuditAction.ACCOUNT_CREATED), captor.capture());
        String message = captor.getValue();
        assertTrue(message.contains("Yeni hesap oluşturuldu"));
        assertTrue(message.contains("ID: 1"));
        assertTrue(message.contains("IBAN: TR123456"));
        assertTrue(message.contains("Bakiye: 1000.00 TRY"));
    }

    @Test
    void shouldLogWhenTransferCompleted() {
        Money amount = Money.of("500.00", Money.Currency.TRY);
        TransferCompletedEvent event = new TransferCompletedEvent(1L, 10L, 20L, amount, TransferStatus.COMPLETED);

        eventListener.onTransferCompleted(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger).log(eq(AuditAction.TRANSFER_EXECUTED), captor.capture());
        String message = captor.getValue();
        assertTrue(message.contains("Para transferi gerçekleştirildi"));
        assertTrue(message.contains("Transfer ID: 1"));
        assertTrue(message.contains("Tutar: 500.00 TRY"));
    }

    @Test
    void shouldLogWhenTransferCancelled() {
        Money amount = Money.of("200.00", Money.Currency.TRY);
        TransferCancelledEvent event = new TransferCancelledEvent(1L, 10L, 20L, amount);

        eventListener.onTransferCancelled(event);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(auditLogger).log(eq(AuditAction.TRANSFER_CANCELLED), captor.capture());
        String message = captor.getValue();
        assertTrue(message.contains("Transfer iptal edildi"));
        assertTrue(message.contains("Transfer ID: 1"));
        assertTrue(message.contains("Geri Yüklenen: 200.00 TRY"));
    }
}
