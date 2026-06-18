package com.bank.app.common.security;

import com.bank.app.user.domain.User;
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

import org.springframework.core.env.Environment;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class JwtServiceTest {

    @Mock private Environment environment;
    private JwtService jwtService;
    private final String defaultSecretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{});
        jwtService = new JwtService(environment, defaultSecretKey, 86400000L, true);
    }

    @Test
    void shouldThrowExceptionWhenDefaultSecretAndProdProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", true);
        IllegalStateException ex1 = assertThrows(IllegalStateException.class, () -> jwtService.validateSecret());
        assertEquals("Default JWT secret is not allowed in production or when allow-default-secret is disabled. Please configure a secure JWT secret key.", ex1.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenDefaultSecretAndAllowDefaultSecretFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", false);
        IllegalStateException ex2 = assertThrows(IllegalStateException.class, () -> jwtService.validateSecret());
        assertEquals("Default JWT secret is not allowed in production or when allow-default-secret is disabled. Please configure a secure JWT secret key.", ex2.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenDefaultSecretAndAllowDefaultSecretTrueAndNotProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", true);
        assertDoesNotThrow(() -> jwtService.validateSecret());
    }

    @Test
    void shouldNotThrowExceptionWhenCustomSecret() {
        ReflectionTestUtils.setField(jwtService, "secretKey", "customSecretKeyWithLongEnoughBytesCustomSecretKeyWithLongEnoughBytes");
        assertDoesNotThrow(() -> jwtService.validateSecret());
    }

    @Test
    void shouldGenerateAndExtractUsernameSuccessfully() {
        User user = new User(100L, "john_doe", "password", "ROLE_USER");

        String token = jwtService.generateToken(user);
        assertNotNull(token);

        String username = jwtService.extractUsername(token);
        assertEquals("john_doe", username);
    }

    @Test
    void shouldExtractCustomClaimUserIdSuccessfully() {
        User user = new User(100L, "john_doe", "password", "ROLE_USER");

        String token = jwtService.generateToken(user);
        Long userId = jwtService.extractClaim(token, claims -> claims.get("userId", Long.class));

        assertEquals(100L, userId);
    }

    @Test
    void shouldValidateTokenSuccessfully() {
        User user = new User(100L, "john_doe", "password", "ROLE_USER");
        String token = jwtService.generateToken(user);

        UserDetails userDetails = new DummyUserDetails("john_doe");

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void shouldReturnFalseWhenValidatingTokenWithDifferentUsername() {
        User user = new User(100L, "john_doe", "password", "ROLE_USER");
        String token = jwtService.generateToken(user);

        UserDetails userDetails = new DummyUserDetails("jane_doe");

        assertFalse(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void shouldThrowExpiredJwtExceptionWhenTokenIsExpired() {
        // Configure JwtService with negative expiration for test
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", -1000L);

        User user = new User(100L, "john_doe", "password", "ROLE_USER");
        String token = jwtService.generateToken(user);

        ExpiredJwtException ex = assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
        assertNotNull(ex.getMessage());
    }

    @Test
    void shouldReturnFalseWhenTokenIsExpiredButNoExceptionThrown() {
        JwtService spyService = spy(jwtService);
        DummyUserDetails userDetails = new DummyUserDetails("john_doe");

        doReturn("john_doe")
            .doReturn(new Date(System.currentTimeMillis() - 10000))
            .when(spyService).extractClaim(eq("expired_token"), any());

        assertFalse(spyService.isTokenValid("expired_token", userDetails));
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
