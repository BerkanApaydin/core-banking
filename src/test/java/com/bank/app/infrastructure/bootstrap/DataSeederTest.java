package com.bank.app.infrastructure.bootstrap;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.usecase.CreateAccountUseCase;
import com.bank.app.account.exception.DuplicateIbanException;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.application.usecase.RegisterUserUseCase;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private RegisterUserUseCase registerUserUseCase;
    @Mock
    private CreateAccountUseCase createAccountUseCase;
    @Mock
    private LoadUserPort loadUserPort;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(registerUserUseCase, createAccountUseCase, loadUserPort);
    }

    @Test
    void shouldSeedUsersAndAccountsViaUseCases() throws Exception {
        User ahmet = new User(1L, "ahmet", "encoded", "ROLE_USER");
        User ayse = new User(2L, "ayse", "encoded", "ROLE_USER");
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.empty(), Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.empty(), Optional.of(ayse));

        CommandLineRunner runner = dataSeeder.seedData();
        runner.run();

        verify(registerUserUseCase).execute(new AuthRequest("ahmet", "ahmet123"));
        verify(registerUserUseCase).execute(new AuthRequest("ayse", "ayse123"));
        verify(createAccountUseCase, atLeastOnce()).execute(any(CreateAccountRequest.class));
    }

    @Test
    void shouldSkipExistingUsers() throws Exception {
        User ahmet = new User(1L, "ahmet", "encoded", "ROLE_USER");
        User ayse = new User(2L, "ayse", "encoded", "ROLE_USER");
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));

        CommandLineRunner runner = dataSeeder.seedData();
        runner.run();

        verify(registerUserUseCase, never()).execute(any());
    }

    @Test
    void shouldSkipDuplicateAccounts() throws Exception {
        User ahmet = new User(1L, "ahmet", "encoded", "ROLE_USER");
        User ayse = new User(2L, "ayse", "encoded", "ROLE_USER");
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));
        doThrow(new DuplicateIbanException("exists")).when(createAccountUseCase)
                .execute(any(CreateAccountRequest.class));

        CommandLineRunner runner = dataSeeder.seedData();
        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    void shouldThrowWhenAhmetCannotBeLoadedAfterRegistration() {
        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.empty(), Optional.empty());

        CommandLineRunner runner = dataSeeder.seedData();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runner.run());

        assertEquals("Ahmet kullanıcısı bulunamadı.", ex.getMessage());

        verify(registerUserUseCase)
                .execute(new AuthRequest("ahmet", "ahmet123"));
    }

    @Test
    void shouldThrowWhenAyseCannotBeLoadedAfterRegistration() {
        User ahmet = new User(1L, "ahmet", "encoded", "ROLE_USER");

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.empty(), Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.empty(), Optional.empty());

        CommandLineRunner runner = dataSeeder.seedData();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runner.run());

        assertEquals("Ayşe kullanıcısı bulunamadı.", ex.getMessage());

        verify(registerUserUseCase)
                .execute(new AuthRequest("ayse", "ayse123"));
    }

    @Test
    void shouldClearSecurityContextAfterExecution() throws Exception {
        User ahmet = new User(1L, "ahmet", "encoded", "ROLE_USER");
        User ayse = new User(2L, "ayse", "encoded", "ROLE_USER");

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.of(ayse));

        CommandLineRunner runner = dataSeeder.seedData();

        runner.run();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueWhenOnlySomeAccountsAlreadyExist() throws Exception {
        User ahmet = new User(1L, "ahmet", "encoded", "ROLE_USER");
        User ayse = new User(2L, "ayse", "encoded", "ROLE_USER");
        AccountResponse response = mock(AccountResponse.class);

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.of(ayse));

        when(createAccountUseCase.execute(any(CreateAccountRequest.class)))
                .thenThrow(new DuplicateIbanException("exists"))
                .thenReturn(response)
                .thenReturn(response);

        CommandLineRunner runner = dataSeeder.seedData();

        assertDoesNotThrow(() -> runner.run());

        verify(createAccountUseCase, times(3))
                .execute(any(CreateAccountRequest.class));

        verify(loadUserPort, atLeastOnce()).findByUsername("ahmet");
        verify(loadUserPort, atLeastOnce()).findByUsername("ayse");
    }
}
