package com.bank.app.account.adapter.in.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AccountStatusResponse")
class AccountStatusResponseTest {

    @Test
    @DisplayName("should expose the account id and the resulting status")
    void shouldExposeAccountIdAndStatus() {
        AccountStatusResponse response = new AccountStatusResponse(42L, "SUSPENDED");

        assertThat(response.accountId()).isEqualTo(42L);
        assertThat(response.status()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("should reject a null account id")
    void shouldRejectNullAccountId() {
        // A null id would serialize as "accountId": null and the client could
        // not tell which account was suspended.
        assertThatThrownBy(() -> new AccountStatusResponse(null, "SUSPENDED"))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Account ID must not be null");
    }

    @Test
    @DisplayName("should reject a null status")
    void shouldRejectNullStatus() {
        // A null status would serialize as "status": null and the client could
        // not distinguish "suspended" from "unknown".
        assertThatThrownBy(() -> new AccountStatusResponse(42L, null))
                .isExactlyInstanceOf(NullPointerException.class)
                .hasMessage("Status must not be null");
    }
}
