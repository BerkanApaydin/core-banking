package com.bank.app.user.adapter.out.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordEncoderAdapterTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void shouldEncodePassword() {
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(passwordEncoder);
        when(passwordEncoder.encode("rawPass")).thenReturn("encodedPass");

        String result = adapter.encode("rawPass");

        assertEquals("encodedPass", result);
    }

    @Test
    void shouldDelegateMatches() {
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(passwordEncoder);
        when(passwordEncoder.matches("raw", "enc")).thenReturn(true);

        assertEquals(true, adapter.matches("raw", "enc"));
    }

    @Test
    void shouldDelegateAMismatch() {
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(passwordEncoder);
        when(passwordEncoder.matches("raw", "enc")).thenReturn(false);

        assertEquals(false, adapter.matches("raw", "enc"));
    }

    @Test
    void shouldFailClosedOnNullMatchesInput() {
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(passwordEncoder);

        assertEquals(false, adapter.matches(null, "enc"));
        assertEquals(false, adapter.matches("raw", null));
    }
}
