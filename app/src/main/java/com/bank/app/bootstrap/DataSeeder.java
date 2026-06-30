package com.bank.app.bootstrap;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.port.in.CreateAccountUseCase;
import com.bank.app.common.domain.Currency;
import com.bank.app.account.domain.exception.DuplicateIbanException;
import com.bank.app.infrastructure.adapter.out.security.CustomUserDetails;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.port.in.RegisterUserUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Örnek verileri domain use case'leri üzerinden tohumlar — JPA entity bypass
 * kaldırıldı.
 */
@Component
@Profile("!prod")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RegisterUserUseCase registerUserUseCase;
    private final CreateAccountUseCase createAccountPort;
    private final LoadUserPort loadUserPort;

    public DataSeeder(RegisterUserUseCase registerUserUseCase,
            CreateAccountUseCase createAccountPort,
            LoadUserPort loadUserPort) {
        this.registerUserUseCase = registerUserUseCase;
        this.createAccountPort = createAccountPort;
        this.loadUserPort = loadUserPort;
    }

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            seedUser("ahmet", "Ahmet123");
            seedUser("ayse", "Ayse1234");

            var ahmet = loadUserPort.findByUsername("ahmet")
                    .orElseThrow(() -> new IllegalStateException("User Ahmet not found."));
            var ayse = loadUserPort.findByUsername("ayse")
                    .orElseThrow(() -> new IllegalStateException("User Ayşe not found."));

            runAsUser(ahmet.getId().value(), ahmet.getUsername(), () -> {
                seedAccountIfAbsent(ahmet.getId().value(), "TR123456789012345678901234", "Ahmet Yılmaz",
                        new BigDecimal("1000.00"), Currency.TRY);
                seedAccountIfAbsent(ahmet.getId().value(), "TR111111111111111111111111", "Ahmet Yılmaz (Dolar Hesabı)",
                        new BigDecimal("2000.00"), Currency.USD);
            });

            runAsUser(ayse.getId().value(), ayse.getUsername(),
                    () -> seedAccountIfAbsent(ayse.getId().value(), "TR987654321098765432109876", "Ayşe Demir",
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

    private void seedAccountIfAbsent(Long userId, String iban, String ownerName,
            BigDecimal balance, Currency currency) {
        try {
            createAccountPort.execute(new CreateAccountRequest(userId, iban, ownerName, balance, currency));
            log.info("Account created");
        } catch (DuplicateIbanException ex) {
            log.debug("Account already exists, skipping");
        }
    }

    private void runAsUser(Long userId, String username, Runnable action) {
        var details = new CustomUserDetails(userId, username, "",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
