package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.in.GetAccountQuery;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.account.application.exception.AccountNotFoundException;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.port.out.SecurityContextPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetAccountUseCaseTest {

    @Mock
    private LoadAccountPort loadAccountPort;

    @Mock
    private SecurityContextPort securityContextPort;

    private GetAccountQuery getAccountUseCase;

    @BeforeEach
    void setUp() {
        getAccountUseCase = new GetAccountUseCaseImpl(loadAccountPort, securityContextPort);
    }

    @Test
    void shouldGetAccountByIdSuccessfully() {
        Account account = new Account(
                1L,
                100L,
                new Iban("TR290006200000000000000123"),
                "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY),
                true);

        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        AccountResponse response = getAccountUseCase.getById(1L);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("TR290006200000000000000123", response.iban());
        assertEquals("Ali Veli", response.ownerName());
        assertEquals(new BigDecimal("500.00"), response.balance());
        assertTrue(response.active());
    }

    @Test
    void shouldThrowExceptionWhenAccountByIdNotFound() {
        when(loadAccountPort.findById(1L)).thenReturn(Optional.empty());

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> {
            getAccountUseCase.getById(1L);
        });

        assertEquals("Hesap bulunamadı. ID: 1", exception.getMessage());
    }

    @Test
    void shouldGetAccountByIbanSuccessfully() {
        Iban iban = new Iban("TR290006200000000000000123");
        Account account = new Account(
                1L,
                100L,
                iban,
                "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY),
                true);

        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));
        doNothing().when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        AccountResponse response = getAccountUseCase.getByIban("TR290006200000000000000123");

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("TR290006200000000000000123", response.iban());
    }

    @Test
    void shouldThrowExceptionWhenAccountByIbanNotFound() {
        Iban iban = new Iban("TR290006200000000000000123");
        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.empty());

        AccountNotFoundException exception = assertThrows(AccountNotFoundException.class, () -> {
            getAccountUseCase.getByIban("TR290006200000000000000123");
        });

        assertEquals("Hesap bulunamadı. IBAN: TR290006200000000000000123", exception.getMessage());
    }

    @Test
    void shouldThrowNullPointerExceptionWhenArgsAreNull() {
        NullPointerException ex1 = assertThrows(NullPointerException.class,
                () -> getAccountUseCase.getById(null));
        assertNotNull(ex1.getMessage());
        NullPointerException ex2 = assertThrows(NullPointerException.class,
                () -> getAccountUseCase.getByIban(null));
        assertNotNull(ex2.getMessage());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOnGetById() {
        Account account = new Account(
                1L,
                100L,
                new Iban("TR290006200000000000000123"),
                "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY),
                true);

        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));
        doThrow(new AccessDeniedException("Bu hesaba erişim yetkiniz yok."))
                .when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> getAccountUseCase.getById(1L));
        assertEquals("Bu hesaba erişim yetkiniz yok.", ex.getMessage());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserIsNotOwnerOnGetByIban() {
        Iban iban = new Iban("TR290006200000000000000123");
        Account account = new Account(
                1L,
                100L,
                iban,
                "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY),
                true);

        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));
        doThrow(new AccessDeniedException("Bu hesaba erişim yetkiniz yok."))
                .when(securityContextPort).checkUserAuthorization(eq(100L), anyString());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> getAccountUseCase.getByIban("TR290006200000000000000123"));
        assertEquals("Bu hesaba erişim yetkiniz yok.", ex.getMessage());
    }

    @Test
    void shouldGetAllAccountsSuccessfully() {
        Account account = new Account(
                1L,
                100L,
                new Iban("TR290006200000000000000123"),
                "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY),
                true);

        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.of(100L));
        when(loadAccountPort.findByUserId(100L)).thenReturn(Collections.singletonList(account));

        List<AccountResponse> results = getAccountUseCase.getAll();

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("TR290006200000000000000123", results.get(0).iban());
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenNotLoggedInOnGetAll() {
        when(securityContextPort.getCurrentUserId()).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> getAccountUseCase.getAll());
    }
}
