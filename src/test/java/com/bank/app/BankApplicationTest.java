package com.bank.app;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.SpringDataAccountRepo;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class BankApplicationTest {

    @Mock
    private SpringDataAccountRepo accountRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldInitDataWhenDatabaseIsEmpty() throws Exception {
        when(userRepo.count()).thenReturn(0L);
        when(accountRepo.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");

        UserJpaEntity ahmet = new UserJpaEntity(1L, "ahmet", "encoded", "ROLE_USER");
        UserJpaEntity ayse = new UserJpaEntity(2L, "ayse", "encoded", "ROLE_USER");
        when(userRepo.save(any(UserJpaEntity.class))).thenReturn(ahmet, ayse);
        when(userRepo.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(userRepo.findByUsername("ayse")).thenReturn(Optional.of(ayse));

        BankApplication app = new BankApplication();
        CommandLineRunner runner = app.initData(accountRepo, userRepo, passwordEncoder);

        runner.run();

        verify(userRepo, times(2)).save(any(UserJpaEntity.class));
        verify(accountRepo, times(3)).save(any(AccountJpaEntity.class));
    }

    @Test
    void shouldSkipInitDataWhenDatabaseHasUsers() throws Exception {
        when(userRepo.count()).thenReturn(1L);
        when(accountRepo.count()).thenReturn(1L);

        BankApplication app = new BankApplication();
        CommandLineRunner runner = app.initData(accountRepo, userRepo, passwordEncoder);

        runner.run();

        verify(userRepo, never()).save(any(UserJpaEntity.class));
        verify(accountRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Test
    void shouldSkipAccountInitWhenDatabaseHasAccounts() throws Exception {
        when(userRepo.count()).thenReturn(0L);
        when(accountRepo.count()).thenReturn(1L);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");

        UserJpaEntity ahmet = new UserJpaEntity(1L, "ahmet", "encoded", "ROLE_USER");
        UserJpaEntity ayse = new UserJpaEntity(2L, "ayse", "encoded", "ROLE_USER");
        when(userRepo.save(any(UserJpaEntity.class))).thenReturn(ahmet, ayse);

        BankApplication app = new BankApplication();
        CommandLineRunner runner = app.initData(accountRepo, userRepo, passwordEncoder);

        runner.run();

        verify(userRepo, times(2)).save(any(UserJpaEntity.class));
        verify(accountRepo, never()).save(any(AccountJpaEntity.class));
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundForAccountInit() {
        when(userRepo.count()).thenReturn(0L);
        when(accountRepo.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");

        UserJpaEntity ahmet = new UserJpaEntity(1L, "ahmet", "encoded", "ROLE_USER");
        when(userRepo.save(any(UserJpaEntity.class))).thenReturn(ahmet);
        when(userRepo.findByUsername("ahmet")).thenReturn(Optional.of(ahmet));
        when(userRepo.findByUsername("ayse")).thenReturn(Optional.empty());

        BankApplication app = new BankApplication();
        CommandLineRunner runner = app.initData(accountRepo, userRepo, passwordEncoder);

        assertThrows(IllegalStateException.class, () -> {
            try {
                runner.run();
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Test
    void shouldThrowExceptionWhenAhmetNotFoundForAccountInit() {
        when(userRepo.count()).thenReturn(0L);
        when(accountRepo.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("encoded_password");

        when(userRepo.save(any(UserJpaEntity.class))).thenReturn(new UserJpaEntity(null, "ahmet", "encoded", "ROLE_USER"));
        when(userRepo.findByUsername("ahmet")).thenReturn(Optional.empty());

        BankApplication app = new BankApplication();
        CommandLineRunner runner = app.initData(accountRepo, userRepo, passwordEncoder);

        assertThrows(IllegalStateException.class, () -> runner.run());
    }

    @Test
    void shouldStartSpringApplication() {
        try (var mockedSpringApplication = mockStatic(SpringApplication.class)) {
            mockedSpringApplication.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                .thenReturn(null);
            assertDoesNotThrow(() -> BankApplication.main(new String[]{}));
        }
    }
}
