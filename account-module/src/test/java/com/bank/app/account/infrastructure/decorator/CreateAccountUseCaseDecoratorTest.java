package com.bank.app.account.infrastructure.decorator;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.application.port.out.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.application.port.out.EventPublisherPort;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.CustomUserDetails;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateAccountUseCaseDecoratorTest {

    @Mock private LoadAccountPort loadAccountPort;
    @Mock private SaveAccountPort saveAccountPort;
    @Mock private EventPublisherPort eventPublisherPort;
    @Mock private SecurityContextPort securityContextPort;

    private CreateAccountUseCaseDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new CreateAccountUseCaseDecorator(
                loadAccountPort, saveAccountPort, eventPublisherPort, securityContextPort);

        CustomUserDetails principal = new CustomUserDetails(100L, "test_user", "password",
                Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal,
                null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldDelegateAndReturnResponse() {
        CreateAccountRequest request = new CreateAccountRequest(
                100L, "TR290006200000000000000123", "Ali Veli",
                new BigDecimal("500.00"), Money.Currency.TRY);

        Account savedAccount = new Account(
                1L, 100L, new Iban("TR290006200000000000000123"), "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY), true);

        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());
        when(loadAccountPort.findByIban(any(Iban.class))).thenReturn(Optional.empty());
        when(saveAccountPort.save(any(Account.class))).thenReturn(savedAccount);

        AccountResponse response = decorator.execute(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("TR290006200000000000000123", response.iban());

        verify(loadAccountPort).findByIban(any(Iban.class));
        verify(saveAccountPort).save(any(Account.class));
        verify(eventPublisherPort).publish(any());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenRequestIsNull() {
        assertThrows(NullPointerException.class, () -> decorator.execute(null));
    }

    @Test
    void shouldBeAnnotatedWithTransactional() {
        assertTrue(decorator.getClass().isAnnotationPresent(Transactional.class),
                "Decorator must be @Transactional");
    }
}
