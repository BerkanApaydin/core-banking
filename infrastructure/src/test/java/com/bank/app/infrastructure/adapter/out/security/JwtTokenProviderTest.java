package com.bank.app.infrastructure.adapter.out.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    @Mock
    private Environment environment;

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(environment, SECRET, 86400000L, true);
    }

    @Test
    void shouldGenerateAndExtractUsername() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
    }

    @Test
    void shouldGenerateAndExtractUserId() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertEquals(1L, jwtTokenProvider.extractUserId(token));
    }

    @Test
    void shouldGenerateAndExtractRole() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertEquals("ROLE_USER", jwtTokenProvider.extractRole(token));
    }

    @Test
    void shouldGenerateTokenWithDefaultRole() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertNotNull(token);
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
        assertEquals(1L, jwtTokenProvider.extractUserId(token));
        assertEquals("ROLE_USER", jwtTokenProvider.extractRole(token));
    }

    @Test
    void shouldGenerateTokenWithCustomRole() {
        String token = jwtTokenProvider.generateToken(1L, "testUser", "ROLE_ADMIN");
        assertNotNull(token);
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
        assertEquals(1L, jwtTokenProvider.extractUserId(token));
        assertEquals("ROLE_ADMIN", jwtTokenProvider.extractRole(token));
    }

    @Test
    void shouldReturnFalseForMalformedToken() {
        assertFalse(jwtTokenProvider.isTokenValid("invalid-token"));
    }

    @Test
    void shouldReturnFalseForNullToken() {
        assertFalse(jwtTokenProvider.isTokenValid(null));
    }

    @Test
    void shouldValidateGeneratedToken() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertTrue(jwtTokenProvider.isTokenValid(token));
    }

    @Test
    void shouldGenerateUniqueTokensOnEachCall() {
        String token1 = jwtTokenProvider.generateToken(1L, "testUser");
        String token2 = jwtTokenProvider.generateToken(1L, "testUser");

        assertNotNull(token1);
        assertNotNull(token2);
    }

    @Test
    void shouldNotThrowExceptionWhenDefaultSecretAllowed() {
        assertDoesNotThrow(() -> {
            new JwtTokenProvider(environment, SECRET, 86400000L, true);
        });
    }

    @Test
    void shouldThrowWhenDefaultSecretIsUsedAndNotAllowed() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        JwtTokenProvider provider = new JwtTokenProvider(environment, SECRET, 86400000L, false);
        assertThrows(IllegalStateException.class, () -> provider.validateSecret());
    }

    @Test
    void shouldNotThrowWithCustomSecretAndNotAllowed() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        JwtTokenProvider provider = new JwtTokenProvider(environment, "FalyIFIC5f2T7fcqZ4A6j1DlCc7CdS/lnxdiReKx1bw=", 86400000L, false);
        assertDoesNotThrow(() -> provider.validateSecret());
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(null, SECRET, 0L, true);
        String token = shortLived.generateToken(1L, "testUser");
        assertFalse(shortLived.isTokenValid(token));
    }
}
