package com.bank.app.user.security;

import com.bank.app.user.infrastructure.security.CustomUserDetailsService;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private LoadUserPort loadUserPort;

    private CustomUserDetailsService service;

    @Test
    void shouldLoadUserSuccessfully() {
        service = new CustomUserDetailsService(loadUserPort);
        User user = new User(100L, "john", "pass", "ROLE_USER");

        when(loadUserPort.findByUsername("john")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("john");

        assertNotNull(details);
        assertEquals("john", details.getUsername());
        assertEquals("pass", details.getPassword());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserNotFound() {
        service = new CustomUserDetailsService(loadUserPort);

        when(loadUserPort.findByUsername("john")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("john"));
        assertEquals("User not found: john", exception.getMessage());
    }
}
