package com.bank.app.infrastructure.bootstrap;

import com.bank.app.account.application.dto.CreateAccountRequest;
import com.bank.app.account.application.usecase.CreateAccountUseCase;
import com.bank.app.common.domain.Money;
import com.bank.app.account.exception.DuplicateIbanException;
import com.bank.app.common.security.CustomUserDetails;
import com.bank.app.user.application.dto.AuthRequest;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.application.usecase.RegisterUserUseCase;
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
 * Örnek verileri domain use case'leri üzerinden tohumlar — JPA entity bypass kaldırıldı.
 */
@Component
@Profile("!prod & !test")
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final RegisterUserUseCase registerUserUseCase;
    private final CreateAccountUseCase createAccountUseCase;
    private final LoadUserPort loadUserPort;

    public DataSeeder(RegisterUserUseCase registerUserUseCase,
                      CreateAccountUseCase createAccountUseCase,
                      LoadUserPort loadUserPort) {
        this.registerUserUseCase = registerUserUseCase;
        this.createAccountUseCase = createAccountUseCase;
        this.loadUserPort = loadUserPort;
    }

    @Bean
    CommandLineRunner seedData() {
        return args -> {
            seedUser("ahmet", "ahmet123");
            seedUser("ayse", "ayse123");

            var ahmet = loadUserPort.findByUsername("ahmet")
                    .orElseThrow(() -> new IllegalStateException("Ahmet kullanıcısı bulunamadı."));
            var ayse = loadUserPort.findByUsername("ayse")
                    .orElseThrow(() -> new IllegalStateException("Ayşe kullanıcısı bulunamadı."));

            runAsUser(ahmet.getId(), ahmet.getUsername(), () -> {
                seedAccountIfAbsent(ahmet.getId(), "TR123456789012345678901234", "Ahmet Yılmaz",
                        new BigDecimal("1000.00"), Money.Currency.TRY);
                seedAccountIfAbsent(ahmet.getId(), "TR111111111111111111111111", "Ahmet Yılmaz (Dolar Hesabı)",
                        new BigDecimal("2000.00"), Money.Currency.USD);
            });

            runAsUser(ayse.getId(), ayse.getUsername(), () ->
                    seedAccountIfAbsent(ayse.getId(), "TR987654321098765432109876", "Ayşe Demir",
                            new BigDecimal("500.00"), Money.Currency.TRY));

            log.info("Veritabanı tohumlama tamamlandı (use case tabanlı).");
        };
    }

    private void seedUser(String username, String password) {
        if (loadUserPort.findByUsername(username).isPresent()) {
            return;
        }
        log.info("Kullanıcı kaydediliyor: {}", username);
        registerUserUseCase.execute(new AuthRequest(username, password));
    }

    private void seedAccountIfAbsent(Long userId, String iban, String ownerName,
                                     BigDecimal balance, Money.Currency currency) {
        try {
            createAccountUseCase.execute(new CreateAccountRequest(userId, iban, ownerName, balance, currency));
            log.info("Hesap oluşturuldu: {}", iban);
        } catch (DuplicateIbanException ex) {
            log.debug("Hesap zaten mevcut, atlanıyor: {}", iban);
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
