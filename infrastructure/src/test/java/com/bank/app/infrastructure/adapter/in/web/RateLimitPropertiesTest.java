package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class RateLimitPropertiesTest {

    @Test
    void shouldUseDefaultValues() {
        RateLimitProperties props = new RateLimitProperties(null, null, 10, 10000, 120, 60000);

        assertEquals(11, props.paths().size());
        assertTrue(props.paths().contains("/api/v1/auth/login"));
        assertTrue(props.paths().contains("/api/v1/auth/browser/login"));
        assertTrue(props.paths().contains("/api/v1/auth/refresh"));
        assertTrue(props.paths().contains("/api/v1/auth/browser/refresh"));
        assertTrue(props.paths().contains("/api/v1/auth/logout"));
        assertTrue(props.paths().contains("/api/v1/auth/browser/logout"));
        assertTrue(props.paths().contains("/api/v1/auth/browser/session"));
        assertTrue(props.paths().contains("/api/v1/admin"));
        assertEquals("caffeine", props.backend());
        assertEquals(10, props.maxRequests());
        assertEquals(10000, props.timeWindowMs());
        assertEquals(RateLimitProperties.DEFAULT_RESOURCE_MAX_REQUESTS, props.resourceMaxRequests());
        assertEquals(RateLimitProperties.DEFAULT_RESOURCE_TIME_WINDOW_MS, props.resourceTimeWindowMs());
    }

    @Test
    void shouldFallBackToDefaultsOnNull() {
        RateLimitProperties props = new RateLimitProperties(null, null, 10, 10000, 120, 60000);

        assertEquals(11, props.paths().size());
        assertEquals("caffeine", props.backend());
    }

    @Test
    void shouldKeepCustomValues() {
        RateLimitProperties props = new RateLimitProperties(List.of("/api/v1/custom"), "redis", 100, 60000, 120, 60000);

        assertEquals(List.of("/api/v1/custom"), props.paths());
        assertEquals("redis", props.backend());
        assertEquals(100, props.maxRequests());
        assertEquals(60000, props.timeWindowMs());
    }

    @Test
    void shouldKeepExplicitEmptyPathsAsKillSwitch() {
        RateLimitProperties props = new RateLimitProperties(List.of(), "caffeine", 10, 10000, 120, 60000);

        assertTrue(props.paths().isEmpty());
    }

    @Test
    void shouldApplyTightAuthTierAndLooseResourceTier() {
        RateLimitProperties props = new RateLimitProperties(null, null, 10, 10000, 120, 60000);

        assertTrue(props.isAuthTier("/api/v1/auth/login"));
        assertTrue(props.isAuthTier("/api/v1/auth/browser/session"));
        assertFalse(props.isAuthTier("/api/v1/accounts"));
        assertFalse(props.isAuthTier("/api/v1/transfers"));
        assertFalse(props.isAuthTier("/api/v1/admin"));
        assertEquals(10, props.maxRequestsFor("/api/v1/auth/register"));
        assertEquals(10000, props.timeWindowMsFor("/api/v1/auth/register"));
        assertEquals(120, props.maxRequestsFor("/api/v1/transfers"));
        assertEquals(60000, props.timeWindowMsFor("/api/v1/accounts"));
    }

    @Test
    void shouldKeepExplicitZeroMaxRequests() {
        RateLimitProperties props = new RateLimitProperties(null, null, 0, 10000, 120, 60000);

        assertEquals(0, props.maxRequests());
    }
}
