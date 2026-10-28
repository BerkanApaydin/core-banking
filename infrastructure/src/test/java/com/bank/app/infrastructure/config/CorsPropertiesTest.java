package com.bank.app.infrastructure.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CorsPropertiesTest {

    @Test
    void shouldNotExposeTheCallerOwnedOriginList() {
        List<String> source = new ArrayList<>(List.of("https://app.example.com"));
        CorsProperties props = new CorsProperties(source);

        source.add("https://evil.example.com");

        assertEquals(List.of("https://app.example.com"), props.allowedOrigins());
        assertThrows(UnsupportedOperationException.class, () -> props.allowedOrigins().add("https://x"));
    }

    @Test
    void shouldDefaultToEmptyWhenOriginsAreNull() {
        CorsProperties props = new CorsProperties(null);

        assertEquals(List.of(), props.allowedOrigins());
    }
}
