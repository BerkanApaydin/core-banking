package com.bank.app.transfer.domain.exception;

import com.bank.app.common.domain.exception.BusinessException;
import com.bank.app.common.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class TransferCurrencyMismatchExceptionTest {

    @Test
    void shouldCreateWithMessage() {
        TransferCurrencyMismatchException ex = new TransferCurrencyMismatchException("Para birimleri uyuşmuyor");
        assertEquals("Para birimleri uyuşmuyor", ex.getMessage());
    }

    @Test
    void shouldBeCurrencyMismatchException() {
        TransferCurrencyMismatchException ex = new TransferCurrencyMismatchException("test");
        assertInstanceOf(CurrencyMismatchException.class, ex);
    }

    @Test
    void shouldBeBusinessException() {
        TransferCurrencyMismatchException ex = new TransferCurrencyMismatchException("test");
        assertInstanceOf(BusinessException.class, ex);
    }

    @Test
    void shouldReturnCorrectErrorCode() {
        TransferCurrencyMismatchException ex = new TransferCurrencyMismatchException("test");
        assertEquals("TRANSFER_CURRENCY_MISMATCH", ex.getErrorCode());
    }
}
