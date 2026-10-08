package com.bank.app.common.domain;

import com.bank.app.common.domain.exception.InvalidIbanException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Iban.checked")
class IbanCheckedTest {

    @Test
    @DisplayName("should accept checksum-valid IBANs")
    void shouldAcceptValidChecksum() {
        Iban bban = Iban.fromTurkishBban("0006200000000000000123");
        assertThat(Iban.checked(bban.value()).value()).isEqualTo(bban.value());
    }

    @Test
    @DisplayName("should reject checksum-invalid IBANs")
    void shouldRejectInvalidChecksum() {
        Iban valid = Iban.fromTurkishBban("0006200000000000000123");
        String corrupted = valid.value().substring(0, valid.value().length() - 1)
                + (valid.value().endsWith("1") ? "2" : "1");
        assertThatThrownBy(() -> Iban.checked(corrupted))
                .isInstanceOf(InvalidIbanException.class);
    }
}
