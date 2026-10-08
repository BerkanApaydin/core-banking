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

    private static final String SECRET = "KqppTj5E0Ofnmy0Zqpes4lcblwsqf50J7huOCLOjsYE=";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 86400000L, 604800000L, true);
    }

    @Test
    void shouldGenerateAndExtractUsername() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
        var verified = jwtTokenProvider.verifyAndDecode(token);
        assertNotNull(verified);
        assertEquals("testUser", verified.username());
        assertEquals(1L, verified.userId());
        assertEquals("ROLE_USER", verified.role());
        assertNotNull(verified.tokenId());
    }

    @Test
    void shouldGenerateAndExposeUserIdViaVerifiedToken() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertEquals(1L, jwtTokenProvider.verifyAndDecode(token).userId());
    }

    @Test
    void shouldGenerateAndExposeRoleViaVerifiedToken() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertEquals("ROLE_USER", jwtTokenProvider.verifyAndDecode(token).role());
    }

    @Test
    void shouldGenerateTokenWithDefaultRole() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertNotNull(token);
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
        var verified = jwtTokenProvider.verifyAndDecode(token);
        assertNotNull(verified);
        assertEquals(1L, verified.userId());
        assertEquals("ROLE_USER", verified.role());
    }

    @Test
    void shouldGenerateTokenWithCustomRole() {
        String token = jwtTokenProvider.generateToken(1L, "testUser", "ROLE_ADMIN");
        assertNotNull(token);
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
        var verified = jwtTokenProvider.verifyAndDecode(token);
        assertNotNull(verified);
        assertEquals(1L, verified.userId());
        assertEquals("ROLE_ADMIN", verified.role());
    }

    @Test
    void shouldRejectMalformedToken() {
        assertNull(jwtTokenProvider.verifyAndDecode("invalid-token"));
    }

    @Test
    void shouldRejectNullToken() {
        assertNull(jwtTokenProvider.verifyAndDecode(null));
    }

    @Test
    void shouldVerifyGeneratedToken() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");
        assertNotNull(jwtTokenProvider.verifyAndDecode(token));
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
        JwtTokenProvider provider = new JwtTokenProvider("FalyIFIC5f2T7fcqZ4A6j1DlCc7CdS/lnxdiReKx1bw=", 86400000L, 604800000L, false);
        assertDoesNotThrow(provider::validateSecret);
        assertNotNull(provider.generateToken(1L, "admin"));
    }

    @Test
    void shouldRejectExpiredToken() {
        // Negative expiration ensures the token is always expired
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -86400000L, 604800000L, true);
        String token = shortLived.generateToken(1L, "testUser");
        assertNull(shortLived.verifyAndDecode(token));
    }

    @Test
    void shouldThrowWhenSecretIsTooShort() {
        JwtTokenProvider provider = new JwtTokenProvider("c2hvcnQ=", 86400000L, 604800000L, true);
        IllegalStateException ex = assertThrows(IllegalStateException.class, provider::validateSecret);
        // Verify the bit-length calculation to kill MathMutator on keyBytes.length * 8
        assertTrue(ex.getMessage().contains("40 bits"));
    }

    @Test
    void shouldThrowWhenSecretIsBlank() {
        JwtTokenProvider blankProvider = new JwtTokenProvider("   ", 86400000L, 604800000L, true);
        IllegalStateException ex = assertThrows(IllegalStateException.class, blankProvider::validateSecret);
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    void shouldThrowWhenDefaultSecretWithoutExplicitConsent() {
        JwtTokenProvider defaultProvider = new JwtTokenProvider(SECRET, 86400000L, 604800000L, false);
        assertThrows(IllegalStateException.class, defaultProvider::validateSecret);
    }

    @Test
    void shouldThrowWhenSecretIsNull() {
        JwtTokenProvider nullProvider = new JwtTokenProvider(null, 86400000L, 604800000L, false);
        IllegalStateException ex = assertThrows(IllegalStateException.class, nullProvider::validateSecret);
        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    void shouldAllowDefaultSecretWithExplicitConsent() {
        JwtTokenProvider consentingProvider = new JwtTokenProvider(SECRET, 86400000L, 604800000L, true);
        assertDoesNotThrow(consentingProvider::validateSecret);
    }

    @Test
    void shouldRejectTokenWithoutSubject() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86400000L, 604800000L, true);
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key)
                .compact();

        assertNull(provider.verifyAndDecode(token));
    }

    @Test
    void shouldRejectTokenWithoutUserIdClaim() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 86400000L, 604800000L, true);
        SecretKey key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET));
        String token = Jwts.builder()
                .subject("testUser")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(key)
                .compact();
        assertNull(provider.verifyAndDecode(token));
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
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -86400000L, 604800000L, true);
        String token = shortLived.generateToken(1L, "testUser");

        assertEquals(0L, shortLived.getRemainingMs(token));
    }

    @Test
    void shouldReturnZeroRemainingMsForMalformedToken() {
        assertEquals(0L, jwtTokenProvider.getRemainingMs("not-a-token"));
    }

    @Test
    void shouldTagAccessAndRefreshTokens() {
        String access = jwtTokenProvider.generateToken(1L, "testUser");
        String refresh = jwtTokenProvider.generateRefreshToken(1L, "testUser", "ROLE_USER");

        assertEquals("access", jwtTokenProvider.extractTokenType(access));
        assertEquals("refresh", jwtTokenProvider.extractTokenType(refresh));
        assertNotEquals(access, refresh);
    }

    @Test
    void shouldDefaultLegacyTokensWithoutTypeToAccess() {
        // Tokens issued before typing carry no "typ" claim (missing => access).
        String legacy = Jwts.builder()
                .subject("testUser")
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertEquals("access", jwtTokenProvider.extractTokenType(legacy));
    }

    @Test
    void shouldRoundTripTokenVersionClaim() {
        String access = jwtTokenProvider.generateToken(1L, "testUser", "ROLE_USER", 7L);
        assertEquals(7L, jwtTokenProvider.extractTokenVersion(access));

        String refresh = jwtTokenProvider.generateRefreshToken(1L, "testUser", "ROLE_USER", 7L);
        assertEquals(7L, jwtTokenProvider.extractTokenVersion(refresh));
    }

    @Test
    void shouldDefaultMissingVersionClaimToZero() {
        // Pre-versioning tokens carry no "ver" claim (missing => generation 0),
        // so rolling deploys never lock users out.
        String legacy = Jwts.builder()
                .subject("testUser")
                .expiration(new Date(System.currentTimeMillis() + 86400000L))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        assertEquals(0L, jwtTokenProvider.extractTokenVersion(legacy));
        assertEquals(0L, jwtTokenProvider.extractTokenVersion(jwtTokenProvider.generateToken(1L, "testUser")));
    }

    @Test
    void shouldUseSeparateRefreshLifetime() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, 900000L, 604800000L, true);

        assertEquals(900000L, provider.getExpirationMs());
        assertEquals(604800000L, provider.getRefreshExpirationMs());

        long accessRemaining = provider.getRemainingMs(provider.generateToken(1L, "testUser"));
        long refreshRemaining = provider.getRemainingMs(
                provider.generateRefreshToken(1L, "testUser", "ROLE_USER"));
        assertTrue(refreshRemaining - accessRemaining > 600_000_000L);
    }
}
