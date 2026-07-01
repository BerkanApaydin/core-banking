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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthFilter;
    @Mock
    private UserDetailsService userDetailsService;
    @Mock
    private CorsConfigurationSource corsConfigurationSource;
    private SecurityConfig securityConfig;
    private SecurityProperties securityProperties;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties(null);
        securityConfig = new SecurityConfig(jwtAuthFilter, userDetailsService, securityProperties);
    }

    @Test
    void shouldCreatePasswordEncoderBean() {
        PasswordEncoder encoder = securityConfig.passwordEncoder();
        assertInstanceOf(BCryptPasswordEncoder.class, encoder);
        assertTrue(encoder.matches("password", encoder.encode("password")));
    }

    @Test
    void shouldCreateAuthenticationProviderBean() {
        AuthenticationProvider provider = securityConfig.authenticationProvider();
        assertInstanceOf(DaoAuthenticationProvider.class, provider);
    }

    @Test
    void shouldCreateSecurityFilterChain() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);
        when(http.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

        SecurityFilterChain chain = securityConfig.securityFilterChain(http, corsConfigurationSource);
        assertNotNull(chain);

        verify(http).build();
    }
}
