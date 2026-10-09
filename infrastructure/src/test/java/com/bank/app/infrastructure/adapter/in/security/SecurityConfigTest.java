package com.bank.app.infrastructure.adapter.in.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthFilter;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private CorsConfigurationSource corsConfigurationSource;
    @Mock
    private ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    @Mock
    private ProblemDetailAccessDeniedHandler accessDeniedHandler;
    private SecurityConfig securityConfig;
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties(null);
        securityConfig = new SecurityConfig(jwtAuthFilter, userDetailsService, securityProperties, authenticationEntryPoint, accessDeniedHandler);
    }

    @Test
    void shouldCreatePasswordEncoderBean() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
        assertTrue(encoder.matches("password", encoder.encode("password")));
    }

    @Test
    void shouldUseBcryptCost12() {
        // I-04: cost factor is pinned at 12 (~250ms/encode). A silent drop to
        // the BCrypt default (10) would weaken stored hashes without any
        // test turning red, so the "$2a$12$" prefix is asserted explicitly.
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String hash = encoder.encode("correct-horse-battery-staple");
        assertTrue(hash.startsWith("$2a$12$"),
                "BCrypt hash must carry cost 12, got: " + hash.substring(0, 7));
    }

    @Test
    void shouldSaltHashesUniquelyAndRejectWrongPasswords() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        String first = encoder.encode("same-password");
        String second = encoder.encode("same-password");
        assertNotEquals(first, second, "BCrypt must use a random salt per encoding");
        assertTrue(encoder.matches("same-password", first));
        assertTrue(encoder.matches("same-password", second));
        assertFalse(encoder.matches("wrong-password", first));
        assertFalse(encoder.matches("", first));
    }

    @Test
    void shouldCreateAuthenticationProviderBean() {
        AuthenticationProvider provider = securityConfig.authenticationProvider();
        assertInstanceOf(DaoAuthenticationProvider.class, provider);
    }
}
