package com.bank.app.transfer.application.usecase;

import org.junit.jupiter.api.Test;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.ConcurrencyFailureException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class TransferRetryableAnnotationTest {

    @Test
    void placeTransferUseCaseShouldHaveRetryableAnnotation() throws Exception {
        Method method = PlaceTransferUseCaseImpl.class
                .getMethod("execute", com.bank.app.transfer.application.dto.TransferRequest.class);

        Retryable retryable = method.getAnnotation(Retryable.class);
        assertNotNull(retryable, "execute method should be annotated with @Retryable");

        assertTrue(retryable.retryFor().length > 0,
                "@Retryable should specify retryFor");
        assertEquals(ConcurrencyFailureException.class, retryable.retryFor()[0],
                "Should retry on ConcurrencyFailureException");
        assertEquals(3, retryable.maxAttempts(),
                "Should have max 3 attempts");
        assertEquals(500, retryable.backoff().delay(),
                "Backoff delay should be 500ms");
        assertEquals(2, retryable.backoff().multiplier(),
                "Backoff multiplier should be 2");
    }

    @Test
    void cancelTransferUseCaseShouldHaveRetryableAnnotation() throws Exception {
        Method method = CancelTransferUseCaseImpl.class
                .getMethod("execute", Long.class);

        Retryable retryable = method.getAnnotation(Retryable.class);
        assertNotNull(retryable, "execute method should be annotated with @Retryable");

        assertEquals(ConcurrencyFailureException.class, retryable.retryFor()[0],
                "Should retry on ConcurrencyFailureException");
        assertEquals(3, retryable.maxAttempts(),
                "Should have max 3 attempts");
        assertEquals(500, retryable.backoff().delay(),
                "Backoff delay should be 500ms");
    }
}
