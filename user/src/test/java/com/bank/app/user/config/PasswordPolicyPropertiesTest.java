package com.bank.app.user.config;

import com.bank.app.user.domain.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyPropertiesTest {

    @Test
    void shouldBuildDefaultPolicy() {
        assertThat(new PasswordPolicyProperties(8, true, true, true).toDomain())
                .isEqualTo(new PasswordPolicy(8, true, true, true));
    }

    @Test
    void shouldRejectWeakMinLength() {
        assertThatThrownBy(() -> new PasswordPolicyProperties(6, true, true, true).toDomain())
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMinLengthAboveBcryptCap() {
        assertThatThrownBy(() -> new PasswordPolicyProperties(100, true, true, true).toDomain())
                .isExactlyInstanceOf(IllegalArgumentException.class);
    }
}
