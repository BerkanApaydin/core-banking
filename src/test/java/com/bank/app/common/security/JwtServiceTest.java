package com.bank.app.common.security;

import com.bank.app.user.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import org.springframework.core.env.Environment;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class JwtServiceTest {

    private JwtService jwtService;
    private Environment environment;
    private final String defaultSecretKey = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{});
        jwtService = new JwtService(environment);
        ReflectionTestUtils.setField(jwtService, "secretKey", defaultSecretKey);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 86400000L); // 24 hours
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", true);
    }

    @Test
    void shouldThrowExceptionWhenDefaultSecretAndProdProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", true);
        assertThrows(IllegalStateException.class, () -> jwtService.validateSecret());
    }

    @Test
    void shouldThrowExceptionWhenDefaultSecretAndAllowDefaultSecretFalse() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        ReflectionTestUtils.setField(jwtService, "allowDefaultSecret", false);
        assertThrows(IllegalStateException.class, () -> jwtService.validateSecret());
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

        assertThrows(ExpiredJwtException.class, () -> jwtService.extractUsername(token));
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
