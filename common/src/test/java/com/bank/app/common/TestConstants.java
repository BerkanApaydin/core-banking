package com.bank.app.common;

import java.math.BigDecimal;

@SuppressWarnings("null")
public final class TestConstants {

    public static final String IBAN_SENDER = "TR770006200000000000000111";
    public static final String IBAN_RECEIVER = "TR870006200000000000000222";
    public static final String IBAN_PASSIVE = "TR970006200000000000000333";
    public static final String IBAN_NONEXISTENT = "TR600006200000000000000999";
    public static final String USERNAME_TEST = "test_user";
    public static final String USERNAME_AHMET = "ahmet";
    public static final String USERNAME_AYSE = "ayse";
    public static final Long USER_ID_1 = 1L;
    public static final Long USER_ID_2 = 2L;
    public static final BigDecimal BALANCE_1000 = new BigDecimal("1000.00");
    public static final BigDecimal BALANCE_500 = new BigDecimal("500.00");
    public static final BigDecimal AMOUNT_200 = new BigDecimal("200.00");
    public static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
    public static final String CURRENCY_TRY = "TRY";

    private TestConstants() {
    }
}
