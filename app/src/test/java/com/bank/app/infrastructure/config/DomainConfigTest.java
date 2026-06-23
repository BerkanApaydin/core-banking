package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class DomainConfigTest {

    @Test
    void shouldCreateDomainConfig() {
        DomainConfig config = new DomainConfig();
        assertNotNull(config);
    }
}
