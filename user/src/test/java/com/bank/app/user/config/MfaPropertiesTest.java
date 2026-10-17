package com.bank.app.user.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MfaPropertiesTest {

    @Test
    void shouldAcceptValidConfiguration() {
        MfaProperties props = new MfaProperties("bank", "a2V5", 30, 6, 1, 15);

        assertThat(props.issuer()).isEqualTo("bank");
        assertThat(props.periodSeconds()).isEqualTo(30);
        assertThat(props.digits()).isEqualTo(6);
        assertThat(props.allowedSkewWindows()).isEqualTo(1);
        assertThat(props.resetTokenValidityMinutes()).isEqualTo(15);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new MfaProperties(" ", "k", 30, 6, 1, 15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties("b", "k", 0, 6, 1, 15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties("b", "k", 30, 5, 1, 15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties("b", "k", 30, 9, 1, 15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties("b", "k", 30, 6, 3, 15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties("b", "k", 30, 6, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MfaProperties("b", "k", 30, 6, 1, 24 * 60 + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
