package com.bank.app.user.security;

import com.bank.app.user.adapter.out.security.UserDetailsAdapter;
import com.bank.app.user.application.port.out.LoadUserPort;
import com.bank.app.user.domain.EmailAddress;
import com.bank.app.user.domain.PhoneNumber;
import com.bank.app.user.domain.Role;
import com.bank.app.user.domain.User;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
@ExtendWith(MockitoExtension.class)
class UserDetailsAdapterTest {

    @Mock
    private LoadUserPort loadUserPort;

    private UserDetailsAdapter service;

    @Test
    void shouldLoadUserSuccessfully() {
        service = new UserDetailsAdapter(loadUserPort);
        User user = new User(new UserId(100L), "john", "pass", Role.ROLE_USER,
                new EmailAddress("john@test.com"), new PhoneNumber("+905551234567"));

        when(loadUserPort.findByUsername("john")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("john");

        assertNotNull(details);
        assertEquals("john", details.getUsername());
        assertEquals("pass", details.getPassword());
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserNotFound() {
        service = new UserDetailsAdapter(loadUserPort);

        when(loadUserPort.findByUsername("john")).thenReturn(Optional.empty());

        UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("john"));
        assertEquals("Kullanıcı bulunamadı: john", exception.getMessage());
    }
}


