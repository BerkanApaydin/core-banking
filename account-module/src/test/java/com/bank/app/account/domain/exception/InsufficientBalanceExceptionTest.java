package com.bank.app.account.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class InsufficientBalanceExceptionTest {

    @Test
    void shouldCreateWithSimpleMessage() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Yetersiz bakiye");
        assertEquals("Yetersiz bakiye", ex.getMessage());
    }

    @Test
    void shouldCreateWithMessageKeyAndArgs() {
        InsufficientBalanceException ex = new InsufficientBalanceException(
                "error.insufficient_balance",
                new Object[]{"100.00", "TRY", "200.00", "TRY"},
                "Bakiye yetersiz. Mevcut: 100.00 TRY, İstenen: 200.00 TRY");
        assertEquals("error.insufficient_balance", ex.getMessageKey());
        assertArrayEquals(new Object[]{"100.00", "TRY", "200.00", "TRY"}, ex.getArgs());
        assertEquals("Bakiye yetersiz. Mevcut: 100.00 TRY, İstenen: 200.00 TRY", ex.getMessage());
    }

    @Test
    void shouldBeBusinessException() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Yetersiz bakiye");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        InsufficientBalanceException ex = new InsufficientBalanceException("Yetersiz bakiye");
        assertEquals("INSUFFICIENT_BALANCE", ex.getErrorCode());
    }
}
