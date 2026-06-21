package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.port.in.AccountInfo;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountQueryServiceDecoratorTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    private AccountQueryServiceDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new AccountQueryServiceDecorator(loadAccountPort);
    }

    @Test
    void shouldDelegateGetAccountInfo() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000123"), "Ali Veli",
                new Money(BigDecimal.TEN, Money.Currency.TRY), true);
        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));

        AccountInfo info = decorator.getAccountInfo(1L);

        assertNotNull(info);
        assertEquals(1L, info.id());
        verify(loadAccountPort).findById(1L);
    }

    @Test
    void shouldDelegateGetAccountInfoForTransfer() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000123"), "Ali Veli",
                new Money(BigDecimal.TEN, Money.Currency.TRY), true);
        when(loadAccountPort.findByIban(any(Iban.class))).thenReturn(Optional.of(account));

        AccountInfo info = decorator.getAccountInfoForTransfer("TR290006200000000000000123");

        assertNotNull(info);
        assertEquals(1L, info.id());
        verify(loadAccountPort).findByIban(any(Iban.class));
    }

    @Test
    void shouldDelegateGetIbansForAccounts() {
        when(loadAccountPort.findByIds(anyCollection())).thenReturn(Collections.emptyList());

        Map<Long, String> result = decorator.getIbansForAccounts(Collections.singletonList(1L));

        assertNotNull(result);
        verify(loadAccountPort).findByIds(anyCollection());
    }

    @Test
    void shouldBeAnnotatedWithTransactional() {
        assertTrue(decorator.getClass().isAnnotationPresent(Transactional.class),
                "Decorator must be @Transactional");
        assertTrue(decorator.getClass().getAnnotation(Transactional.class).readOnly(),
                "Transactional annotation must be readOnly");
    }
}
