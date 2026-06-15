package com.bank.app;

import com.bank.app.account.infrastructure.persistence.AccountJpaEntity;
import com.bank.app.account.infrastructure.persistence.SpringDataAccountRepo;
import com.bank.app.user.infrastructure.persistence.UserRepository;
import com.bank.app.user.infrastructure.persistence.UserJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableRetry(order = 99)
@EnableScheduling
public class BankApplication {

    private static final Logger log = LoggerFactory.getLogger(BankApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }

    @Bean
    @Profile("!prod & !test")
    public CommandLineRunner initData(SpringDataAccountRepo accountRepo, UserRepository userRepo,
            PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepo.count() == 0) {
                log.info("Veritabanı örnek kullanıcı verileriyle tohumlanıyor...");
                userRepo.save(new UserJpaEntity(
                        null,
                        "ahmet",
                        passwordEncoder.encode("ahmet123"),
                        "ROLE_USER"));
                userRepo.save(new UserJpaEntity(
                        null,
                        "ayse",
                        passwordEncoder.encode("ayse123"),
                        "ROLE_USER"));
            }

            if (accountRepo.count() == 0) {
                log.info("Veritabanı örnek hesap verileriyle tohumlanıyor...");

                UserJpaEntity ahmet = userRepo.findByUsername("ahmet")
                        .orElseThrow(() -> new IllegalStateException("Ahmet kullanıcısı bulunamadı."));
                UserJpaEntity ayse = userRepo.findByUsername("ayse")
                        .orElseThrow(() -> new IllegalStateException("Ayşe kullanıcısı bulunamadı."));

                accountRepo.save(new AccountJpaEntity(
                        null,
                        ahmet.getId(),
                        "TR123456789012345678901234",
                        "Ahmet Yılmaz",
                        new BigDecimal("1000.00"),
                        "TRY",
                        true));

                accountRepo.save(new AccountJpaEntity(
                        null,
                        ayse.getId(),
                        "TR987654321098765432109876",
                        "Ayşe Demir",
                        new BigDecimal("500.00"),
                        "TRY",
                        true));

                accountRepo.save(new AccountJpaEntity(
                        null,
                        ahmet.getId(),
                        "TR111111111111111111111111",
                        "Ahmet Yılmaz (Dolar Hesabı)",
                        new BigDecimal("2000.00"),
                        "USD",
                        true));

                log.info("Tohumlama tamamlandı.");
            } else {
                log.info("Veritabanı zaten tohumlanmış veya veri içeriyor, tohumlama atlanıyor.");
            }
        };
    }
}
