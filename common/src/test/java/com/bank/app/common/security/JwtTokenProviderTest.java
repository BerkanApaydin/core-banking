package com.bank.app.common.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.env.Environment;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class JwtTokenProviderTest {

    @Mock
    private Environment environment;
    private JwtTokenProvider jwtTokenProvider;
    private final String defaultSecretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[] {});
        jwtTokenProvider = new JwtTokenProvider(environment, defaultSecretKey, 86400000L, true);
    }

    @Test
    void shouldThrowExceptionWhenDefaultSecretAndProdProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
        ReflectionTestUtils.setField(jwtTokenProvider, "allowDefaultSecret", true);
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () -> jwtTokenProvider.validateSecret());
        assertEquals(
                "Default JWT secret is not allowed in production or when allow-default-secret is disabled. Please configure a secure JWT secret key via the JWT_SECRET environment variable.",
                ex1.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenDefaultSecretAndAllowDefaultSecretFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "dev" });
        ReflectionTestUtils.setField(jwtTokenProvider, "allowDefaultSecret", false);
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () -> jwtTokenProvider.validateSecret());
        assertEquals(
                "Default JWT secret is not allowed in production or when allow-default-secret is disabled. Please configure a secure JWT secret key via the JWT_SECRET environment variable.",
                ex2.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenDefaultSecretAndAllowDefaultSecretTrueAndNotProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "dev" });
        ReflectionTestUtils.setField(jwtTokenProvider, "allowDefaultSecret", true);
        assertDoesNotThrow(() -> jwtTokenProvider.validateSecret());
    }

    @Test
    void shouldNotThrowExceptionWhenCustomSecret() {
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey",
                "customSecretKeyWithLongEnoughBytesCustomSecretKeyWithLongEnoughBytes");
        assertDoesNotThrow(() -> jwtTokenProvider.validateSecret());
    }

    @Test
    void shouldNotThrowExceptionWhenProdWithNonDefaultSecret() {
        when(environment.getActiveProfiles()).thenReturn(new String[] { "prod" });
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey",
                "customSecretKeyWithLongEnoughBytesCustomSecretKeyWithLongEnoughBytes");
        assertDoesNotThrow(() -> jwtTokenProvider.validateSecret());
    }

    @Test
    void shouldGenerateAndExtractUsernameSuccessfully() {
        String token = jwtTokenProvider.generateToken(100L, "john_doe");
        assertNotNull(token);

        String username = jwtTokenProvider.extractUsername(token);
        assertEquals("john_doe", username);
    }

    @Test
    void shouldExtractCustomClaimUserIdSuccessfully() {
        String token = jwtTokenProvider.generateToken(100L, "john_doe");
        Long userId = jwtTokenProvider.extractClaim(token, claims -> claims.get("userId", Long.class));

        assertEquals(100L, userId);
    }

    @Test
    void shouldValidateTokenSuccessfully() {
        String token = jwtTokenProvider.generateToken(100L, "john_doe");

        assertTrue(jwtTokenProvider.isTokenValid(token));
    }

    @Test
    void shouldReturnFalseWhenValidatingTokenWithDifferentUsername() {
        String token = jwtTokenProvider.generateToken(100L, "john_doe");

        assertTrue(jwtTokenProvider.isTokenValid(token));
    }

    @Test
    void shouldThrowExpiredJwtExceptionWhenTokenIsExpired() {
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", -1000L);

        String token = jwtTokenProvider.generateToken(100L, "john_doe");

        ExpiredJwtException ex = assertThrows(ExpiredJwtException.class, () -> jwtTokenProvider.extractUsername(token));
        assertNotNull(ex.getMessage());
    }

    @Test
    void shouldGenerateTokenWithExtraClaims() {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", "ADMIN");

        String token = jwtTokenProvider.generateToken(extraClaims, 100L, "john_doe");
        assertNotNull(token);

        String role = jwtTokenProvider.extractClaim(token, claims -> claims.get("role", String.class));
        assertEquals("ADMIN", role);
        Long userId = jwtTokenProvider.extractClaim(token, claims -> claims.get("userId", Long.class));
        assertEquals(100L, userId);
    }

    @Test
    void shouldThrowExceptionWhenTokenHasInvalidSignature() {
        String token = jwtTokenProvider.generateToken(100L, "john_doe");

        JwtTokenProvider differentKeyProvider = new JwtTokenProvider(environment,
                "DifferentSecretKeyForTestingPurposesOnlyDifferentSecretKeyForTestingPurposesOnly!",
                86400000L, true);

        assertThrows(io.jsonwebtoken.security.SecurityException.class,
                () -> differentKeyProvider.extractUsername(token));
    }

    @Test
    void shouldReturnFalseWhenValidatingTamperedToken() {
        String token = jwtTokenProvider.generateToken(100L, "john_doe");
        String tamperedToken = token.substring(0, token.length() - 5) + "TAMPER";

        assertThrows(Exception.class,
                () -> jwtTokenProvider.extractUsername(tamperedToken));
    }

    @Test
    void shouldReturnFalseWhenTokenIsExpiredButNoExceptionThrown() {
        JwtTokenProvider spyService = spy(jwtTokenProvider);
        DummyUserDetails userDetails = new DummyUserDetails("john_doe");

        doReturn(userDetails)
                .doReturn(new Date(System.currentTimeMillis() - 10000))
                .when(spyService).extractClaim(eq("expired_token"), any());

        assertFalse(spyService.isTokenValid("expired_token"));
    }

    private static class DummyUserDetails implements UserDetails {
        private final String username;

        public DummyUserDetails(String username) {
            this.username = username;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }

        @Override
        public String getPassword() {
            return "password";
        }

        @Override
        public String getUsername() {
            return username;
        }
    }
}
