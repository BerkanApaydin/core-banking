package com.bank.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

@SuppressWarnings("null")
class BankApplicationTest {

    @Test
    void shouldCreateInstance() {
        BankApplication app = new BankApplication();
        assertNotNull(app);
    }

    @Test
    void shouldStartSpringApplication() {
        try (var mockedSpringApplication = mockStatic(SpringApplication.class)) {
            mockedSpringApplication.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                    .thenReturn(null);
            assertDoesNotThrow(() -> BankApplication.main(new String[]{}));
        }
    }
}
