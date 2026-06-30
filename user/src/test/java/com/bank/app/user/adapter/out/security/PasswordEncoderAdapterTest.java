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
}
