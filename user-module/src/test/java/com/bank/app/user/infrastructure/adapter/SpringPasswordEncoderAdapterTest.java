package com.bank.app.user.infrastructure.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringPasswordEncoderAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    private SpringPasswordEncoderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringPasswordEncoderAdapter(passwordEncoder);
    }

    @Test
    void shouldEncodePassword() {
        when(passwordEncoder.encode("rawPassword")).thenReturn("$2a$10$encodedHash");

        String result = adapter.encode("rawPassword");

        assertEquals("$2a$10$encodedHash", result);
        verify(passwordEncoder).encode("rawPassword");
    }

    @Test
    void shouldEncodeEmptyString() {
        when(passwordEncoder.encode("")).thenReturn("");

        String result = adapter.encode("");

        assertEquals("", result);
    }

    @Test
    void shouldPropagateNullPointerException() {
        when(passwordEncoder.encode(null)).thenThrow(new IllegalArgumentException("Password must not be null"));

        assertThrows(IllegalArgumentException.class, () -> adapter.encode(null));
    }
}
