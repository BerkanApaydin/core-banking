package com.bank.app.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * R4: full {@code @Configuration} home for the seeder runner.
 *
 * <p>Previously the {@code @Bean} lived in lite mode inside
 * {@link DataSeeder} ({@code @Component}). Functionally equivalent, but a
 * dedicated configuration class keeps the service bean free of container
 * concerns and makes the profile gate visible at the wiring site. The profile
 * expression mirrors {@link DataSeeder}'s: double-gated on purpose, so the
 * runner can never fire under {@code prod} even in an accidental
 * {@code prod,dev} combination.
 */
@Configuration
@Profile("(dev | demo) & !prod")
public class DataSeederRunnerConfig {

    @Bean
    CommandLineRunner seedData(DataSeeder dataSeeder) {
        return dataSeeder.seedData();
    }
}
