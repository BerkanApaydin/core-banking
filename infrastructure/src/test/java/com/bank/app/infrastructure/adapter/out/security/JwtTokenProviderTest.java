package com.bank.app.infrastructure.adapter.out.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 86400000L, true);
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
        assertNotEquals(token1, token2);
    }

    @Test
    void shouldNotThrowWithCustomSecret() {
        JwtTokenProvider provider = new JwtTokenProvider(
                "FalyIFIC5f2T7fcqZ4A6j1DlCc7CdS/lnxdiReKx1bw=", 86400000L, false);
        assertDoesNotThrow(provider::validateSecret);
        assertNotNull(provider.generateToken(1L, "admin"));
    }

    @Test
    void shouldReturnFalseForExpiredToken() {
        // Negative expiration ensures the token is always expired
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -86400000L, true);
        String token = shortLived.generateToken(1L, "testUser");
        assertFalse(shortLived.isTokenValid(token));
    }

    @Test
    void shouldThrowWhenSecretIsTooShort() {
        JwtTokenProvider provider = new JwtTokenProvider("c2hvcnQ=", 86400000L, true);
        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::validateSecret);
        // Verify the bit-length calculation to kill MathMutator on keyBytes.length * 8
        assertTrue(ex.getMessage().contains("40 bits"));
    }

    @Test
    void shouldThrowWhenSecretIsBlank() {
        JwtTokenProvider blankProvider = new JwtTokenProvider("   ", 86400000L, true);
        IllegalStateException ex = assertThrows(IllegalStateException.class, blankProvider::validateSecret);
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    void shouldThrowWhenDefaultSecretWithoutExplicitConsent() {
        JwtTokenProvider defaultProvider = new JwtTokenProvider(SECRET, 86400000L, false);
        assertThrows(IllegalStateException.class, defaultProvider::validateSecret);
    }

    @Test
    void shouldThrowWhenSecretIsNull() {
        JwtTokenProvider nullProvider = new JwtTokenProvider(null, 86400000L, false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, nullProvider::validateSecret);
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    void shouldAllowDefaultSecretWithExplicitConsent() {
        JwtTokenProvider consentingProvider = new JwtTokenProvider(SECRET, 86400000L, true);
        assertDoesNotThrow(consentingProvider::validateSecret);
    }

    @Test
    void shouldReturnFalseForTokenWithoutSubject() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86400000L, true);
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key)
                .compact();

        assertFalse(provider.isTokenValid(token));
    }

    @Test
    void shouldReturnNullExtractUserIdWhenNoUserIdClaim() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86400000L, true);
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .subject("testUser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key)
                .compact();
        assertNull(provider.extractUserId(token));
    }

    @Test
    void shouldReturnExpirationMs() {
        assertEquals(86400000L, jwtTokenProvider.getExpirationMs());
    }

    @Test
    void shouldReturnPositiveRemainingMsForFreshToken() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");

        long remaining = jwtTokenProvider.getRemainingMs(token);

        assertTrue(remaining > 0 && remaining <= 86400000L);
    }

    @Test
    void shouldReturnZeroRemainingMsForExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -86400000L, true);
        String token = shortLived.generateToken(1L, "testUser");

        assertEquals(0L, shortLived.getRemainingMs(token));
    }

    @Test
    void shouldReturnZeroRemainingMsForMalformedToken() {
        assertEquals(0L, jwtTokenProvider.getRemainingMs("not-a-token"));
    }
}
