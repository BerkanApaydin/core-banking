package com.bank.app.common.security;

import com.bank.app.user.application.port.LoadUserPort;
import com.bank.app.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {

    @Test
    void shouldLoadUserSuccessfully() {
        LoadUserPort loadUserPort = mock(LoadUserPort.class);
        CustomUserDetailsService service = new CustomUserDetailsService(loadUserPort);
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
        LoadUserPort loadUserPort = mock(LoadUserPort.class);
        CustomUserDetailsService service = new CustomUserDetailsService(loadUserPort);

        when(loadUserPort.findByUsername("john")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("john"));
    }
}
