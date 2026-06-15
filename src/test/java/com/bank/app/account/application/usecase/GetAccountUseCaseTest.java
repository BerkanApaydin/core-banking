package com.bank.app.account.application.usecase;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.port.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.exception.AccountNotFoundException;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.SecurityUtils;
import com.bank.app.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetAccountUseCaseTest {

    private LoadAccountPort loadAccountPort;
    private SecurityUtils securityUtils;
    private GetAccountUseCase getAccountUseCase;

    @BeforeEach
    void setUp() {
        loadAccountPort = mock(LoadAccountPort.class);
        securityUtils = new SecurityUtils();
        getAccountUseCase = new GetAccountUseCase(loadAccountPort, securityUtils);

        // Set default authenticated user context using CustomUserDetails
        CustomUserDetails principal = new CustomUserDetails(100L, "test_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        assertThrows(NullPointerException.class, () -> getAccountUseCase.getById(null));
        assertThrows(NullPointerException.class, () -> getAccountUseCase.getByIban(null));
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

        // Authenticate with a different user ID (999L)
        CustomUserDetails principal = new CustomUserDetails(999L, "other_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(loadAccountPort.findById(1L)).thenReturn(Optional.of(account));

        assertThrows(AccessDeniedException.class, () -> getAccountUseCase.getById(1L));
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

        // Authenticate with a different user ID (999L)
        CustomUserDetails principal = new CustomUserDetails(999L, "other_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(account));

        assertThrows(AccessDeniedException.class, () -> getAccountUseCase.getByIban("TR290006200000000000000123"));
    }
}
