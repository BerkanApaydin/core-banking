package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.out.LoadAccountPort;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAccountUseCaseDecoratorTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SecurityContextPort securityContextPort;

    private GetAccountUseCaseDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new GetAccountUseCaseDecorator(loadAccountPort, securityContextPort);
    }

    @Test
    void shouldDelegateGetById() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000123"), "Ali Veli",
                new Money(BigDecimal.TEN, Money.Currency.TRY), true);
        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        AccountResponse response = decorator.getById(1L);

        assertNotNull(response);
        assertEquals(1L, response.id());
        verify(loadAccountPort).findById(1L);
    }

    @Test
    void shouldDelegateGetByIban() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000123"), "Ali Veli",
                new Money(BigDecimal.TEN, Money.Currency.TRY), true);
        when(loadAccountPort.findByIban(any(Iban.class))).thenReturn(Optional.of(account));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        AccountResponse response = decorator.getByIban("TR290006200000000000000123");

        assertNotNull(response);
        assertEquals(1L, response.id());
        verify(loadAccountPort).findByIban(any(Iban.class));
    }

    @Test
    void shouldDelegateGetAll() {
        Account account = new Account(1L, 100L, new Iban("TR290006200000000000000123"), "Ali Veli",
                new Money(BigDecimal.TEN, Money.Currency.TRY), true);
        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.of(100L));
        when(loadAccountPort.findByUserId(100L)).thenReturn(Collections.singletonList(account));

        List<AccountResponse> list = decorator.getAll();

        assertNotNull(list);
        assertEquals(1, list.size());
        verify(loadAccountPort).findByUserId(100L);
    }

    @Test
    void shouldBeAnnotatedWithTransactional() {
        assertTrue(decorator.getClass().isAnnotationPresent(Transactional.class),
                "Decorator must be @Transactional");
        assertTrue(decorator.getClass().getAnnotation(Transactional.class).readOnly(),
                "Transactional annotation must be readOnly");
    }
}
