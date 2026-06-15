package com.bank.app.account.application.usecase;

import com.bank.app.audit.application.service.AuditService;
import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.LoadAccountPort;
import com.bank.app.account.application.port.SaveAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.Iban;
import com.bank.app.common.exception.DuplicateIbanException;
import com.bank.app.common.domain.Money;
import com.bank.app.common.security.SecurityUtils;
import com.bank.app.common.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateAccountUseCaseTest {

    private LoadAccountPort loadAccountPort;
    private SaveAccountPort saveAccountPort;
    private AuditService auditService;
    private SecurityUtils securityUtils;
    private CreateAccountUseCase createAccountUseCase;

    @BeforeEach
    void setUp() {
        loadAccountPort = mock(LoadAccountPort.class);
        saveAccountPort = mock(SaveAccountPort.class);
        auditService = mock(AuditService.class);
        securityUtils = new SecurityUtils();
        createAccountUseCase = new CreateAccountUseCase(loadAccountPort, saveAccountPort, auditService, securityUtils);

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
    void shouldCreateAccountSuccessfully() {
        CreateAccountRequest request = new CreateAccountRequest(
                100L,
                "TR290006200000000000000123",
                "Ali Veli",
                new BigDecimal("500.00"),
                Money.Currency.TRY);

        Iban iban = new Iban(request.iban());
        Account expectedAccount = new Account(
                1L,
                100L,
                iban,
                "Ali Veli",
                new Money(new BigDecimal("500.00"), Money.Currency.TRY),
                true);

        when(loadAccountPort.findByIban(iban))
                .thenReturn(Optional.empty()) // Mükerrer IBAN yok
                .thenReturn(Optional.of(expectedAccount)); // Kayıttan sonra getirme işlemi

        AccountResponse response = createAccountUseCase.execute(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("TR290006200000000000000123", response.iban());
        assertEquals("Ali Veli", response.ownerName());
        assertEquals(new BigDecimal("500.00"), response.balance());
        assertEquals("TRY", response.currency());
        assertTrue(response.active());

        verify(saveAccountPort).save(any(Account.class));
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenCreatingAccountForAnotherUser() {
        CreateAccountRequest request = new CreateAccountRequest(
                200L, // ID is 200
                "TR290006200000000000000123",
                "Ali Veli",
                new BigDecimal("500.00"),
                Money.Currency.TRY);

        // Authenticated as user ID 100 using CustomUserDetails
        CustomUserDetails principal = new CustomUserDetails(100L, "ali_user", "password", Collections.emptyList());
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(AccessDeniedException.class, () -> createAccountUseCase.execute(request));
        verify(saveAccountPort, never()).save(any(Account.class));
    }

    @Test
    void shouldThrowExceptionWhenIbanAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest(
                100L,
                "TR290006200000000000000123",
                "Ali Veli",
                new BigDecimal("500.00"),
                Money.Currency.TRY);

        Iban iban = new Iban(request.iban());
        Account existingAccount = new Account(
                1L,
                100L,
                iban,
                "Eski Sahip",
                new Money(new BigDecimal("100.00"), Money.Currency.TRY),
                true);

        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.of(existingAccount));

        DuplicateIbanException exception = assertThrows(DuplicateIbanException.class, () -> {
            createAccountUseCase.execute(request);
        });

        assertEquals("Bu IBAN ile kayıtlı bir hesap zaten mevcut: TR290006200000000000000123", exception.getMessage());
        verify(saveAccountPort, never()).save(any(Account.class));
    }

    @Test
    void shouldThrowExceptionWhenCurrencyIsNull() {
        CreateAccountRequest request = new CreateAccountRequest(
                100L,
                "TR290006200000000000000123",
                "Ali Veli",
                new BigDecimal("500.00"),
                null);

        Iban iban = new Iban(request.iban());
        when(loadAccountPort.findByIban(iban)).thenReturn(Optional.empty());

        assertThrows(NullPointerException.class, () -> {
            createAccountUseCase.execute(request);
        });

        verify(saveAccountPort, never()).save(any(Account.class));
    }

    @Test
    void shouldThrowNullPointerExceptionWhenRequestIsNull() {
        assertThrows(NullPointerException.class, () -> createAccountUseCase.execute(null));
    }

    @Test
    void shouldThrowAccessDeniedExceptionWhenUserNotLoggedIn() {
        CreateAccountRequest request = new CreateAccountRequest(
                100L,
                "TR290006200000000000000123",
                "Ali Veli",
                new BigDecimal("500.00"),
                Money.Currency.TRY);

        // Clear security context to simulate no logged in user
        SecurityContextHolder.clearContext();

        assertThrows(AccessDeniedException.class, () -> createAccountUseCase.execute(request));
        verify(saveAccountPort, never()).save(any(Account.class));
    }
}
