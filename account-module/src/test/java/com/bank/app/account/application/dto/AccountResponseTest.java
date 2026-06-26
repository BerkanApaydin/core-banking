package com.bank.app.account.application.dto;

import com.bank.app.account.domain.Account;
import com.bank.app.account.domain.AccountStatus;
import com.bank.app.common.domain.Iban;
import com.bank.app.common.domain.Currency;
import com.bank.app.common.domain.Money;
import com.bank.app.common.domain.UserId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("null")
class AccountResponseTest {

    @Test
    void shouldMapFromActiveAccount() {
        Account account = Account.builder()
                .id(1L)
                .userId(new UserId(10L))
                .iban(new Iban("TR123456789012345678901234"))
                .ownerName("Ahmet")
                .balance(new Money(new BigDecimal("1000.00"), Currency.TRY))
                .status(AccountStatus.ACTIVE)
                .build();

        AccountResponse response = AccountResponse.from(account);

        assertEquals(1L, response.id());
        assertEquals(10L, response.userId());
        assertEquals("TR123456789012345678901234", response.iban());
        assertEquals("Ahmet", response.ownerName());
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.balance()));
        assertEquals("TRY", response.currency());
        assertEquals(AccountStatus.ACTIVE, response.status());
        assertTrue(response.active());
    }

    @Test
    void shouldMapFromInactiveAccount() {
        Account account = Account.builder()
                .id(2L)
                .userId(new UserId(20L))
                .iban(new Iban("TR987654321098765432109876"))
                .ownerName("Mehmet")
                .balance(new Money(new BigDecimal("500.00"), Currency.USD))
                .status(AccountStatus.CLOSED)
                .build();

        AccountResponse response = AccountResponse.from(account);

        assertEquals(2L, response.id());
        assertEquals(20L, response.userId());
        assertEquals("TR987654321098765432109876", response.iban());
        assertEquals("Mehmet", response.ownerName());
        assertEquals(0, new BigDecimal("500.00").compareTo(response.balance()));
        assertEquals("USD", response.currency());
        assertEquals(AccountStatus.CLOSED, response.status());
        assertFalse(response.active());
    }
}
