package com.bank.app.infrastructure.adapter.in.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class JpaAuditingConfigTest {

    private JpaAuditingConfig config;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        config = new JpaAuditingConfig();
        originalContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(SecurityContextHolder.createEmptyContext());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    void shouldReturnSystemWhenAuthIsNull() {
        AuditorAware<String> provider = config.auditorProvider();
        Optional<String> auditor = provider.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    void shouldReturnSystemWhenNotAuthenticated() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuditorAware<String> provider = config.auditorProvider();
        Optional<String> auditor = provider.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    void shouldReturnSystemWhenAnonymousToken() {
        AnonymousAuthenticationToken auth = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuditorAware<String> provider = config.auditorProvider();
        Optional<String> auditor = provider.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    void shouldReturnSystemWhenAnonymousName() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuditorAware<String> provider = config.auditorProvider();
        Optional<String> auditor = provider.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("system", auditor.get());
    }

    @Test
    void shouldReturnValidUserWhenAuthenticated() {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "john_doe", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        AuditorAware<String> provider = config.auditorProvider();
        Optional<String> auditor = provider.getCurrentAuditor();
        assertTrue(auditor.isPresent());
        assertEquals("john_doe", auditor.get());
    }
}
