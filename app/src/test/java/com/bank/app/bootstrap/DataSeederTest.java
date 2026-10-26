package com.bank.app.bootstrap;

import com.bank.app.account.application.dto.AccountResponse;
import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.account.domain.Account;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.exception.InvalidIbanException;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.User;
import com.bank.app.common.domain.UserId;
import com.bank.app.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    private static final DemoSeedProperties SEED_PROPERTIES =
            new DemoSeedProperties("Ahmet12345678", "Ayse12345678");

    @Mock
    private RegisterUserUseCase registerUserUseCase;
    @Mock
    private CreateAccountUseCase createAccountPort;
    @Mock
    private LoadUserPort loadUserPort;
    @Mock
    private LoadAccountPort loadAccountPort;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder(registerUserUseCase, createAccountPort, loadUserPort,
                loadAccountPort, SEED_PROPERTIES);
    }

    @Test
    void shouldSeedUsersAndAccountsViaUseCases() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.empty()).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.empty()).thenReturn(Optional.of(ayse));

        CommandLineRunner runner = dataSeeder.seedData();
        runner.run();

        verify(registerUserUseCase).execute(new AuthRequest("ahmet", SEED_PROPERTIES.ahmetPassword()));
        verify(registerUserUseCase).execute(new AuthRequest("ayse", SEED_PROPERTIES.aysePassword()));
        verify(createAccountPort, atLeastOnce()).execute(any(CreateAccountRequest.class));
    }

    @Test
    void shouldSkipExistingUsers() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));

        CommandLineRunner runner = dataSeeder.seedData();
        runner.run();

        verify(registerUserUseCase, never()).execute(any());
    }

    @Test
    void shouldSkipDuplicateAccounts() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));
        doThrow(new DuplicateIbanException("exists")).when(createAccountPort)
                .execute(any(CreateAccountRequest.class));

        CommandLineRunner runner = dataSeeder.seedData();
        assertDoesNotThrow(() -> runner.run());
    }

    @Test
    void shouldThrowWhenAhmetCannotBeLoadedAfterRegistration() {
        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.empty()).thenReturn(Optional.empty());

        CommandLineRunner runner = dataSeeder.seedData();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runner.run());

        assertEquals("User Ahmet not found.", ex.getMessage());

        verify(registerUserUseCase)
                .execute(new AuthRequest("ahmet", SEED_PROPERTIES.ahmetPassword()));
    }

    @Test
    void shouldThrowWhenAyseCannotBeLoadedAfterRegistration() {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.empty()).thenReturn(Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.empty()).thenReturn(Optional.empty());

        CommandLineRunner runner = dataSeeder.seedData();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> runner.run());

        assertEquals("User Ayse not found.", ex.getMessage());

        verify(registerUserUseCase)
                .execute(new AuthRequest("ayse", SEED_PROPERTIES.aysePassword()));
    }

    @Test
    void shouldClearSecurityContextAfterExecution() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.of(ayse));

        CommandLineRunner runner = dataSeeder.seedData();

        runner.run();

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldSetAuthenticationOnRunAsUser() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.of(ayse));

        doAnswer(invocation -> {
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            return mock(AccountResponse.class);
        }).when(createAccountPort).execute(any(CreateAccountRequest.class));

        CommandLineRunner runner = dataSeeder.seedData();
        runner.run();
    }

    @Test
    void shouldContinueWhenOnlySomeAccountsAlreadyExist() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        AccountResponse response = mock(AccountResponse.class);

        when(loadUserPort.findByUsername("ahmet"))
                .thenReturn(Optional.of(ahmet));

        when(loadUserPort.findByUsername("ayse"))
                .thenReturn(Optional.of(ayse));

        when(createAccountPort.execute(any(CreateAccountRequest.class)))
                .thenThrow(new DuplicateIbanException("exists"))
                .thenReturn(response)
                .thenReturn(response);

        CommandLineRunner runner = dataSeeder.seedData();

        assertDoesNotThrow(() -> runner.run());

        verify(createAccountPort, times(3))
                .execute(any(CreateAccountRequest.class));

        verify(loadUserPort, atLeastOnce()).findByUsername("ahmet");
        verify(loadUserPort, atLeastOnce()).findByUsername("ayse");
    }

    @Test
    void seedPrincipalShouldExposeIdentity() {
        var principal = new DataSeeder.SeedPrincipal(7L, "seed",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertEquals(7L, principal.getAuthenticatedUserId());
        assertEquals("seed", principal.getAuthenticatedUsername());
    }

    @Test
    void shouldNotCreateASecondFundedAccountForLegacyDemoIban() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));
        when(loadAccountPort.findByIban(new Iban("TR123456789012345678901234")))
                .thenReturn(Optional.of(mock(Account.class)));

        dataSeeder.seedData().run();

        verify(createAccountPort, times(2)).execute(any(CreateAccountRequest.class));
    }

    @Test
    void shouldUseConfiguredSeedPasswords() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.empty()).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.empty()).thenReturn(Optional.of(ayse));
        DataSeeder seeder = new DataSeeder(registerUserUseCase, createAccountPort, loadUserPort,
                loadAccountPort, new DemoSeedProperties("CustomAhmet123!", "CustomAyse123!"));

        seeder.seedData().run();

        verify(registerUserUseCase).execute(new AuthRequest("ahmet", "CustomAhmet123!"));
        verify(registerUserUseCase).execute(new AuthRequest("ayse", "CustomAyse123!"));
    }

    @Test
    void shouldFailFastWhenSeedPasswordIsBlank() {
        DataSeeder seeder = new DataSeeder(registerUserUseCase, createAccountPort, loadUserPort,
                loadAccountPort, new DemoSeedProperties(" ", "Ayse12345678"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> seeder.seedData().run());

        assertThat(failure).hasMessageContaining("app.demo.seed.ahmet-password");
    }

    @Test
    void shouldFailFastWhenSeedPasswordIsNull() {
        DataSeeder seeder = new DataSeeder(registerUserUseCase, createAccountPort, loadUserPort,
                loadAccountPort, new DemoSeedProperties(null, "Ayse12345678"));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> seeder.seedData().run());

        assertThat(failure).hasMessageContaining("app.demo.seed.ahmet-password");
    }

    /**
     * Only {@link DuplicateIbanException} is absorbed. Any other failure
     * aborts the whole run: the remaining accounts are never seeded (a
     * half-seeded demo environment) and the CommandLineRunner failure aborts
     * the application startup.
     */
    @Test
    void shouldAbortTheWholeSeedRunWhenAnAccountFailsForANonDuplicateReason() {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));
        doThrow(new InvalidIbanException("Invalid IBAN format"))
                .when(createAccountPort).execute(any(CreateAccountRequest.class));

        CommandLineRunner runner = dataSeeder.seedData();

        assertThrows(InvalidIbanException.class, runner::run);

        verify(createAccountPort, times(1)).execute(any(CreateAccountRequest.class));
    }

    /**
     * The shipped default password is blank, so enabling the demo profile
     * without the env var must fail loudly — naming the env var — BEFORE any
     * user or account is written.
     */
    @Test
    void shouldFailFastNamingTheEnvVarWhenTheBlankDefaultPasswordIsUsed() {
        DataSeeder seeder = new DataSeeder(registerUserUseCase, createAccountPort, loadUserPort,
                loadAccountPort, new DemoSeedProperties("", ""));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> seeder.seedData().run());

        assertThat(failure).hasMessageContaining("app.demo.seed.ahmet-password");
        assertThat(failure).hasMessageContaining("DEMO_SEED_AHMET_PASSWORD");
        verify(registerUserUseCase, never()).execute(any());
        verify(createAccountPort, never()).execute(any());
    }

    /**
     * The "already seeded" guard only asks about the LEGACY IBAN, so an
     * account that exists under the NEW IBAN is invisible to it.
     */
    @Test
    void shouldProbeOnlyTheLegacyIbanWhenDecidingToSkipASeedAccount() throws Exception {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));

        dataSeeder.seedData().run();

        ArgumentCaptor<Iban> probed = ArgumentCaptor.forClass(Iban.class);
        verify(loadAccountPort, atLeastOnce()).findByIban(probed.capture());
        assertThat(probed.getAllValues()).extracting(Iban::value)
                .containsExactlyInAnyOrder(
                        "TR123456789012345678901234",
                        "TR111111111111111111111111",
                        "TR987654321098765432109876");
    }

    /**
     * Because the guard only probes the LEGACY IBAN, the create is still
     * attempted when the account already exists under the NEW IBAN: the
     * second funded account is prevented only by the account module's
     * duplicate-IBAN guard, not by the seeder.
     */
    @Test
    void shouldStillAttemptTheCreateWhenOnlyTheNewIbanAlreadyExists() {
        User ahmet = User.rehydrate(new UserId(1L), "ahmet", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        User ayse = User.rehydrate(new UserId(2L), "ayse", "$2a$12$testencodedhash000000000000000000000001", Role.ROLE_USER);
        when(loadUserPort.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(loadUserPort.findByUsername("ayse")).thenReturn(Optional.of(ayse));
        when(loadAccountPort.findByIban(any(Iban.class))).thenReturn(Optional.empty());
        doThrow(new DuplicateIbanException("TR963456789012345678901234"))
                .when(createAccountPort).execute(any(CreateAccountRequest.class));

        CommandLineRunner runner = dataSeeder.seedData();

        assertDoesNotThrow(() -> runner.run());

        ArgumentCaptor<CreateAccountRequest> created = ArgumentCaptor.forClass(CreateAccountRequest.class);
        verify(createAccountPort, times(3)).execute(created.capture());
        assertThat(created.getAllValues()).extracting(CreateAccountRequest::iban)
                .containsExactlyInAnyOrder(
                        "TR963456789012345678901234",
                        "TR721111111111111111111111",
                        "TR137654321098765432109876");
    }
}
