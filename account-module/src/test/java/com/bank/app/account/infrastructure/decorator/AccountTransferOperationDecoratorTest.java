package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountTransferOperationDecoratorTest {

    @Mock private LoadAccountPort loadAccountPort;
    @Mock private SaveAccountPort saveAccountPort;
    @Mock private SecurityContextPort securityContextPort;

    private AccountTransferOperationDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new AccountTransferOperationDecorator(loadAccountPort, saveAccountPort, securityContextPort);
    }

    @Test
    void shouldDelegateExecuteTransfer() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Sender",
                new Money(new BigDecimal("100.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 100L, new Iban("TR290006200000000000000222"), "Receiver",
                new Money(new BigDecimal("100.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        decorator.executeTransfer(1L, 2L, new Money(new BigDecimal("50.00"), Money.Currency.TRY));

        verify(loadAccountPort).findByIdWithLock(1L);
        verify(loadAccountPort).findByIdWithLock(2L);
        verify(saveAccountPort, times(2)).save(any(Account.class));
    }

    @Test
    void shouldDelegateReverseTransfer() {
        Account sender = new Account(1L, 100L, new Iban("TR290006200000000000000111"), "Sender",
                new Money(new BigDecimal("100.00"), Money.Currency.TRY), true);
        Account receiver = new Account(2L, 100L, new Iban("TR290006200000000000000222"), "Receiver",
                new Money(new BigDecimal("100.00"), Money.Currency.TRY), true);

        when(loadAccountPort.findByIdWithLock(1L)).thenReturn(Optional.of(sender));
        when(loadAccountPort.findByIdWithLock(2L)).thenReturn(Optional.of(receiver));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        decorator.reverseTransfer(1L, 2L, new Money(new BigDecimal("50.00"), Money.Currency.TRY));

        verify(loadAccountPort).findByIdWithLock(1L);
        verify(loadAccountPort).findByIdWithLock(2L);
        verify(saveAccountPort, times(2)).save(any(Account.class));
    }

    @Test
    void shouldBeAnnotatedWithTransactional() {
        assertTrue(decorator.getClass().isAnnotationPresent(Transactional.class),
                "Decorator must be @Transactional");
    }
}
