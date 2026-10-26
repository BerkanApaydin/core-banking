package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.ClockProviderPort;
import com.bank.app.infrastructure.adapter.in.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({"null", "deprecation"})
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // Zero-entropy placeholder: valid 256-bit length, but uniform content never
    // flags secret scanners. Tests needing the REAL dev default reference
    // JwtTokenProvider.DEFAULT_JWT_SECRET explicitly (see below).
    private static final String SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final String SECRET_OLD = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=";

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
    void shouldStampIssuerAudienceAndKeyId() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");

        var parsed = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(token);

        assertEquals("clean-architecture-bank", parsed.getPayload().getIssuer());
        assertTrue(parsed.getPayload().getAudience().contains("clean-architecture-bank-api"));
        assertEquals("primary", parsed.getHeader().getKeyId());
    }

    @Test
    void shouldRejectTokenWithWrongIssuerOrAudience() {
        JwtTokenProvider verifier = provider("test-issuer", "test-audience");
        JwtTokenProvider wrongIssuer = provider("other-issuer", "test-audience");
        JwtTokenProvider wrongAudience = provider("test-issuer", "other-audience");

        assertNull(verifier.verifyAndDecode(wrongIssuer.generateToken(1L, "testUser")));
        assertNull(verifier.verifyAndDecode(wrongAudience.generateToken(1L, "testUser")));
    }

    @Test
    void shouldAcceptTokenWithinConfiguredClockSkew() {
        JwtTokenProvider verifier = provider("test-issuer", "test-audience");

        assertNotNull(verifier.verifyAndDecode(expiredBySeconds(10L)));
        assertNull(verifier.verifyAndDecode(expiredBySeconds(40L)));
    }

    @Test
    void shouldVerifyAgainstInjectedClock() {
        java.util.concurrent.atomic.AtomicReference<Clock> now =
                new java.util.concurrent.atomic.AtomicReference<>(
                        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        ClockProviderPort clockProvider = now::get;
        JwtTokenProvider provider = provider("test-issuer", "test-audience", clockProvider);
        String token = provider.generateToken(1L, "testUser");

        now.set(Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC));
        assertNotNull(provider.verifyAndDecode(token));

        now.set(Clock.fixed(Instant.parse("2026-01-03T00:00:00Z"), ZoneOffset.UTC));
        assertNull(provider.verifyAndDecode(token));
        assertEquals(0L, provider.getRemainingMs(token));
    }

    @Test
    void shouldExtendBlacklistTtlByClockSkew() {
        JwtTokenProvider verifier = provider("test-issuer", "test-audience");

        long remaining = verifier.getRemainingMs(expiredBySeconds(10L));

        assertTrue(remaining > 0 && remaining <= 30_000L);
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
        // Uses the real dev default (not the placeholder): this test pins the
        // production refusal path, so it must track the constant, not a copy.
        JwtTokenProvider defaultProvider = new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_JWT_SECRET, 86400000L, 604800000L, false);
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
        JwtTokenProvider consentingProvider = new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_JWT_SECRET, 86400000L, 604800000L, true);
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
    void shouldRejectNonBase64SecretWithActionableMessage() {
        // A pasted/short non-Base64 secret must fail at startup with the fix
        // command, not with a raw DecodingException the operator cannot act on.
        JwtTokenProvider invalid = new JwtTokenProvider(
                "!!!!not-base64!!!!", 86400000L, 604800000L, true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                invalid::validateSecret);

        assertTrue(ex.getMessage().contains("valid Base64"));
        assertNotNull(ex.getCause());
    }

    // --- lazy initialisation (@PostConstruct never ran) ---

    @Test
    void shouldBuildTheParserLazilyWhenPostConstructNeverRan() {
        // Outside Spring the parser is built on first use: a token minted by a
        // sibling instance with the same secret must still verify (no NPE,
        // no "cannot invoke" failure).
        JwtTokenProvider issuer = new JwtTokenProvider(SECRET, 86400000L, 604800000L, true);
        String token = issuer.generateToken(1L, "testUser");

        JwtTokenProvider fresh = new JwtTokenProvider(SECRET, 86400000L, 604800000L, true);

        assertNotNull(fresh.verifyAndDecode(token));
        assertEquals("testUser", fresh.verifyAndDecode(token).username());
    }

    @Test
    void shouldFailWithAnActionableErrorWhenTheLazySecretIsNotBase64() {
        // The lazy path must surface the same actionable message as the eager
        // one, not a bare NPE from the half-initialised provider.
        JwtTokenProvider invalid = new JwtTokenProvider(
                "!!!!not-base64!!!!", 86400000L, 604800000L, true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> invalid.verifyAndDecode("any-token"));

        assertTrue(ex.getMessage().contains("valid Base64"));
    }

    @Test
    void shouldFailWithAnActionableErrorWhenTheLazySecretIsBlank() {
        JwtTokenProvider blank = new JwtTokenProvider("   ", 86400000L, 604800000L, true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> blank.verifyAndDecode("any-token"));

        assertTrue(ex.getMessage().contains("must not be blank"));
    }

    @Test
    void shouldRefuseTheDefaultSecretOnFirstUseWhenNotExplicitlyAllowed() {
        JwtTokenProvider refusing = new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_JWT_SECRET, 86400000L, 604800000L, false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> refusing.verifyAndDecode("any-token"));

        assertTrue(ex.getMessage().contains("Default JWT secret is not allowed"));
    }

    @Test
    void shouldRejectBlankToken() {
        assertNull(jwtTokenProvider.verifyAndDecode("   "));
    }

    @Test
    void shouldRejectTokenMissingAnyRequiredClaim() {
        // Each shape is a valid signature but must never become a
        // half-valid VerifiedToken (downstream code assumes all claims).
        assertNull(jwtTokenProvider.verifyAndDecode(
                tokenWithClaims("user", 1L, null, true, null)));
        assertNull(jwtTokenProvider.verifyAndDecode(
                tokenWithClaims("   ", 1L, "ROLE_USER", true, null)));
        assertNull(jwtTokenProvider.verifyAndDecode(
                tokenWithClaims("user", 1L, "  ", true, null)));
        assertNull(jwtTokenProvider.verifyAndDecode(
                tokenWithClaims("user", null, "ROLE_USER", true, null)));
    }

    @Test
    void shouldRejectTokenWithoutExpirationAndReportZeroRemaining() {
        String token = tokenWithClaims("user", 1L, "ROLE_USER", false, null);

        assertNull(jwtTokenProvider.verifyAndDecode(token));
        assertEquals(0L, jwtTokenProvider.getRemainingMs(token));
    }

    @Test
    void shouldFailClosedForNullAndBlankTokenReaders() {
        assertEquals("access", jwtTokenProvider.extractTokenType(null));
        assertEquals("access", jwtTokenProvider.extractTokenType("   "));
        assertEquals(0L, jwtTokenProvider.extractTokenVersion(null));
        assertEquals(0L, jwtTokenProvider.getRemainingMs(null));
    }

    @Test
    void shouldTreatBlankTokenTypeAsAccess() {
        // A forged/blank type must degrade toward the non-refresh flow.
        String token = tokenWithClaims("user", 1L, "ROLE_USER", true, "  ");

        assertEquals("access", jwtTokenProvider.extractTokenType(token));
    }

    @Test
    void shouldReturnExpirationMs() {
        assertEquals(86400000L, jwtTokenProvider.getExpirationMs());
    }

    @Test
    void shouldReturnPositiveRemainingMsForFreshToken() {
        String token = jwtTokenProvider.generateToken(1L, "testUser");

        long remaining = jwtTokenProvider.getRemainingMs(token);

        // The TTL includes the 30s skew grace so blacklist entries outlive it.
        assertTrue(remaining > 0 && remaining <= 86_430_000L);
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

    @Test
    void shouldVerifyTokenSignedWithRetiredKey() {
        JwtTokenProvider provider = providerWithRetiredKeys(
                Map.of("test-key-old", SECRET_OLD));

        var verified = provider.verifyAndDecode(tokenWithKeyId("test-key-old", SECRET_OLD));

        assertNotNull(verified);
        assertEquals(1L, verified.userId());
        assertEquals("testUser", verified.username());
    }

    @Test
    void shouldRejectTokenWithUnknownKeyId() {
        JwtTokenProvider provider = providerWithRetiredKeys(
                Map.of("test-key-old", SECRET_OLD));

        assertNull(provider.verifyAndDecode(tokenWithKeyId("unknown-key", SECRET_OLD)));
    }

    @Test
    void shouldStillSignNewTokensWithActiveKeyIdWhenRetiredKeysExist() {
        JwtTokenProvider provider = providerWithRetiredKeys(
                Map.of("test-key-old", SECRET_OLD));

        var parsed = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .build()
                .parseSignedClaims(provider.generateToken(1L, "testUser"));

        assertEquals("test-key", parsed.getHeader().getKeyId());
    }

    private static JwtTokenProvider provider(String issuer, String audience) {
        return provider(issuer, audience, Clock::systemUTC);
    }

    private static JwtTokenProvider provider(String issuer, String audience,
            ClockProviderPort clockProvider) {
        return new JwtTokenProvider(new JwtProperties(
                SECRET, 86400000L, 604800000L, true,
                issuer, audience, "test-key", 30L, Map.of()), clockProvider);
    }

    private static JwtTokenProvider providerWithRetiredKeys(Map<String, String> retiredKeys) {
        return new JwtTokenProvider(new JwtProperties(
                SECRET, 86400000L, 604800000L, true,
                "test-issuer", "test-audience", "test-key", 30L, retiredKeys),
                Clock::systemUTC);
    }

    private static String tokenWithKeyId(String keyId, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer("test-issuer")
                .audience().add("test-audience").and()
                .header().keyId(keyId).and()
                .subject("testUser")
                .claim("userId", 1L)
                .claim("role", "ROLE_USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 86_400_000L))
                .signWith(key)
                .compact();
    }

    private static String tokenWithClaims(String subject, Long userId, String role,
            boolean withExpiration, String tokenType) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        var builder = Jwts.builder()
                .issuer("clean-architecture-bank")
                .audience().add("clean-architecture-bank-api").and()
                .header().keyId("primary").and()
                .subject(subject)
                .issuedAt(new Date());
        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        if (tokenType != null) {
            builder.claim("typ", tokenType);
        }
        if (withExpiration) {
            builder.expiration(new Date(System.currentTimeMillis() + 86400000L));
        }
        return builder.signWith(key).compact();
    }

    private static String expiredBySeconds(long seconds) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .issuer("test-issuer")
                .audience().add("test-audience").and()
                .header().keyId("test-key").and()
                .subject("testUser")
                .claim("userId", 1L)
                .claim("role", "ROLE_USER")
                .issuedAt(new Date(now - 3_600_000L))
                .expiration(new Date(now - seconds * 1000L))
                .signWith(key)
                .compact();
    }
}
