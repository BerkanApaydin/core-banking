package com.bank.app.account.application.port.out;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoadAccountPortTest {

    @Test
    void shouldDefensivelyCopyAccountPageContent() {
        var source = new ArrayList<com.bank.app.account.domain.Account>();
        var page = new LoadAccountPort.AccountPage(source, 0);

        source.add(null);

        assertThat(page.content()).isEmpty();
        assertThatThrownBy(() -> page.content().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNormalizeNullAccountPageContentToEmpty() {
        var page = new LoadAccountPort.AccountPage(null, 0);

        assertThat(page.content()).isEmpty();
    }
}
