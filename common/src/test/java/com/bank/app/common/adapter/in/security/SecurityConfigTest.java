package com.bank.app.common.adapter.in.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import com.bank.app.common.adapter.in.config.CorsProperties;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock private JwtAuthenticationFilter jwtAuthFilter;
    @Mock private UserDetailsService userDetailsService;
    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(jwtAuthFilter, userDetailsService,
                new CorsProperties(List.of("http://localhost:3000")));
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
    void shouldCreateCorsConfigurationSourceBean() {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        assertNotNull(source);
        assertNotNull(source.getCorsConfiguration(new MockHttpServletRequest()));
    }

    @Test
    void shouldCreateSecurityFilterChain() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class, RETURNS_DEEP_STUBS);
        when(http.build()).thenReturn(mock(DefaultSecurityFilterChain.class));

        SecurityFilterChain chain = securityConfig.securityFilterChain(http);
        assertNotNull(chain);

        verify(http).build();
    }
}
