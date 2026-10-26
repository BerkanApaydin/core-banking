package com.bank.testsupport.clock;

import com.bank.app.common.application.port.out.ClockProviderPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/**
 * Test-only clock overrides. Kept outside the {@code com.bank.app} scan root
 * so {@code @SpringBootTest} cannot discover it implicitly; tests that need a
 * mutable clock opt in with {@code @Import(ClockConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    public MutableClock mutableClock() {
        return new MutableClock(Instant.now());
    }

    @Bean
    @Primary
    public ClockProviderPort mutableClockProvider(MutableClock clock) {
        return () -> clock;
    }
}
