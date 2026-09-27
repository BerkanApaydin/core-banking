package com.bank.app.account.adapter.out.iban;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureRandomIbanGeneratorTest {

    @Test
    void generatesChecksumValidSimulationIban() {
        var iban = new SecureRandomIbanGenerator().generate();

        assertThat(iban.value()).matches("TR[0-9]{24}");
        assertThat(iban.value().substring(4, 10)).isEqualTo("000000");
        assertThat(iban.hasValidChecksum()).isTrue();
    }
}
