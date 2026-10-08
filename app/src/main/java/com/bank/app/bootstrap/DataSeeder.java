package com.bank.app.bootstrap;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.account.application.port.out.LoadAccountPort;
import com.bank.app.common.application.port.out.AuthenticatedPrincipalPort;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Iban;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * Seeds sample data through domain use cases — JPA entity bypass removed.
 *
 * <p>R4: this class is a plain domain-service bean (no {@code @Bean} methods —
 * lite-mode {@code @Bean} in a {@code @Component} skips full
 * {@code @Configuration} proxying). The {@code CommandLineRunner} wiring lives
 * in {@link DataSeederRunnerConfig}.
 *
 * <p>The profile expression is intentional: {@code (dev | demo) & !prod} keeps
 * seeding off even when {@code dev} is accidentally combined with {@code prod}
 * (e.g. {@code prod,dev}), whereas {@code {"dev","demo"}} would fire. Never
 * simplify it to a plain name list.
 */
@Component
@Profile("(dev | demo) & !prod")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RegisterUserUseCase registerUserUseCase;
    private final CreateAccountUseCase createAccountPort;
    private final LoadUserPort loadUserPort;
    private final LoadAccountPort loadAccountPort;

    public DataSeeder(RegisterUserUseCase registerUserUseCase,
            CreateAccountUseCase createAccountPort,
            LoadUserPort loadUserPort,
            LoadAccountPort loadAccountPort) {
        this.registerUserUseCase = registerUserUseCase;
        this.createAccountPort = createAccountPort;
        this.loadUserPort = loadUserPort;
        this.loadAccountPort = loadAccountPort;
    }

    /**
     * Factory for the startup runner. Plain method (not a {@code @Bean}):
     * {@link DataSeederRunnerConfig} exposes it to the context, which keeps
     * this class unit-testable without Spring ({@code new DataSeeder(...).seedData()}).
     */
    public CommandLineRunner seedData() {
        return args -> {
            seedUser("ahmet", "Ahmet12345678");
            seedUser("ayse", "Ayse12345678");

            var ahmet = loadUserPort.findByUsername("ahmet")
                    .orElseThrow(() -> new IllegalStateException("User Ahmet not found."));
            var ayse = loadUserPort.findByUsername("ayse")
                    .orElseThrow(() -> new IllegalStateException("User Ayse not found."));

            runAsUser(ahmet.getId().value(), ahmet.getUsername(), () -> {
                seedAccountIfAbsent(ahmet.getId().value(), "TR963456789012345678901234",
                        "TR123456789012345678901234", "Ahmet Yılmaz",
                        new BigDecimal("1000.00"), Currency.TRY);
                seedAccountIfAbsent(ahmet.getId().value(), "TR721111111111111111111111",
                        "TR111111111111111111111111", "Ahmet Yılmaz (Dolar Hesabı)",
                        new BigDecimal("2000.00"), Currency.USD);
            });

            runAsUser(ayse.getId().value(), ayse.getUsername(),
                    () -> seedAccountIfAbsent(ayse.getId().value(), "TR137654321098765432109876",
                            "TR987654321098765432109876", "Ayşe Demir",
                            new BigDecimal("500.00"), Currency.TRY));

            log.info("Database seeding completed (use case based).");
        };
    }

    private void seedUser(String username, String password) {
        if (loadUserPort.findByUsername(username).isPresent()) {
            return;
        }
        log.info("Saving user: {}", username);
        registerUserUseCase.execute(new AuthRequest(username, password));
    }

    private void seedAccountIfAbsent(Long userId, String iban, String legacyIban, String ownerName,
            BigDecimal balance, Currency currency) {
        // Earlier demo releases used the same BBAN with invalid check digits.
        // Do not mint a second opening balance when upgrading an existing DB.
        if (loadAccountPort.findByIban(new Iban(legacyIban)).isPresent()) {
            log.warn("Legacy demo account is present; skipping replacement seed account");
            return;
        }
        try {
            createAccountPort.execute(new CreateAccountRequest(userId, iban, ownerName, balance, currency));
            log.info("Account created");
        } catch (DuplicateIbanException ex) {
            log.debug("Account already exists, skipping");
        }
    }

    private void runAsUser(Long userId, String username, Runnable action) {
        var details = new SeedPrincipal(userId, username,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Bootstrap-owned principal so {@code app} never depends on an
     * infrastructure concrete adapter class. Consumed only through
     * {@link AuthenticatedPrincipalPort} by the security adapter.
     */
    static final class SeedPrincipal extends User
            implements AuthenticatedPrincipalPort {

        private static final long serialVersionUID = 1L;

        private final Long userId;

        SeedPrincipal(Long userId, String username,
                Collection<? extends GrantedAuthority> authorities) {
            super(username, "", authorities);
            this.userId = userId;
        }

        @Override
        public Long getAuthenticatedUserId() {
            return userId;
        }

        @Override
        public String getAuthenticatedUsername() {
            return getUsername();
        }
    }
}
