package com.bank.app.infrastructure.adapter.out.clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SystemClockProvider")
class SystemClockProviderTest {

    @Test
    @DisplayName("should return the injected clock")
    void shouldReturnInjectedClock() {
        Clock fixed = Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneId.of("UTC"));
        SystemClockProvider provider = new SystemClockProvider(fixed);

        assertSame(fixed, provider.clock());
    }

    @Test
    @DisplayName("should use system default clock when null is passed")
    void shouldUseSystemDefaultWhenNull() {
        SystemClockProvider provider = new SystemClockProvider(null);

        assertNotNull(provider.clock());
        assertEquals(Clock.systemDefaultZone().getZone(), provider.clock().getZone());
    }
}
