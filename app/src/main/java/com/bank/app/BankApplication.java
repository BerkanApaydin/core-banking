package com.bank.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// Explicit per-BC persistence packages instead of a broad "com.bank.app" scan:
// every bounded context (and platform persistence) must opt in, so a new
// module can never silently join — or leave — the persistence unit.
// Single persistence unit is intentional: transfer+account mutations commit
// atomically in one local transaction (see UseCaseTransactionAspect).
@EnableJpaRepositories(basePackages = {
        "com.bank.app.account.adapter.out.persistence",
        "com.bank.app.transfer.adapter.out.persistence",
        "com.bank.app.user.adapter.out.persistence",
        "com.bank.app.audit.adapter.out.persistence",
        "com.bank.app.infrastructure.adapter.out.persistence",
        "com.bank.app.persistence"
})
@EntityScan(basePackages = {
        "com.bank.app.account.adapter.out.persistence",
        "com.bank.app.transfer.adapter.out.persistence",
        "com.bank.app.user.adapter.out.persistence",
        "com.bank.app.audit.adapter.out.persistence",
        "com.bank.app.infrastructure.adapter.out.persistence",
        "com.bank.app.persistence"
})
@EnableAsync
@EnableScheduling
public class BankApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }
}
