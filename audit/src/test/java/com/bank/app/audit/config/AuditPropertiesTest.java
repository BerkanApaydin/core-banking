package com.bank.app.audit.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditPropertiesTest {

    @Test
    void shouldRejectNonPositiveMaxQueryLimit() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new AuditProperties(0, true, 365));
        assertTrue(failure.getMessage().contains("max query limit"));
    }

    @Test
    void shouldRejectNonPositiveRetentionDays() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new AuditProperties(500, true, 0));
        assertTrue(failure.getMessage().contains("retention days"));
    }

    @Test
    void shouldKeepValidValues() {
        AuditProperties props = new AuditProperties(500, true, 365);

        assertEquals(500, props.maxQueryLimit());
        assertTrue(props.retentionEnabled());
        assertEquals(365, props.retentionDays());
    }
}
