package com.bank.app.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class RateLimitPropertiesTest {

    @Test
    void shouldUseDefaultValues() {
        RateLimitProperties props = new RateLimitProperties();

        assertEquals(4, props.getPaths().size());
        assertTrue(props.getPaths().contains("/api/v1/auth/login"));
        assertEquals("caffeine", props.getBackend());
        assertEquals(10, props.getMaxRequests());
        assertEquals(10000, props.getTimeWindowMs());
    }

    @Test
    void shouldSetAndGetPaths() {
        RateLimitProperties props = new RateLimitProperties();
        List<String> customPaths = List.of("/api/v1/custom");
        props.setPaths(customPaths);

        assertEquals(customPaths, props.getPaths());
    }

    @Test
    void shouldSetAndGetBackend() {
        RateLimitProperties props = new RateLimitProperties();
        props.setBackend("redis");

        assertEquals("redis", props.getBackend());
    }

    @Test
    void shouldSetAndGetMaxRequests() {
        RateLimitProperties props = new RateLimitProperties();
        props.setMaxRequests(100);

        assertEquals(100, props.getMaxRequests());
    }

    @Test
    void shouldSetAndGetTimeWindowMs() {
        RateLimitProperties props = new RateLimitProperties();
        props.setTimeWindowMs(60000);

        assertEquals(60000, props.getTimeWindowMs());
    }

    @Test
    void shouldHandleEmptyPaths() {
        RateLimitProperties props = new RateLimitProperties();
        props.setPaths(List.of());

        assertTrue(props.getPaths().isEmpty());
    }

    @Test
    void shouldHandleZeroMaxRequests() {
        RateLimitProperties props = new RateLimitProperties();
        props.setMaxRequests(0);

        assertEquals(0, props.getMaxRequests());
    }
}
