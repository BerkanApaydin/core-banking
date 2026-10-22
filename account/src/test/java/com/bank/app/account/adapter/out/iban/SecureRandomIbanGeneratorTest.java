package com.bank.app.account.adapter.out.iban;

import com.bank.app.common.domain.Iban;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SecureRandomIbanGeneratorTest {

    private static final int SAMPLE_SIZE = 2000;

    @Test
    void generatesChecksumValidSimulationIban() {
        var iban = new SecureRandomIbanGenerator().generate();

        assertThat(iban.value()).matches("TR[0-9]{24}");
        assertThat(iban.value().substring(4, 10)).isEqualTo("000000");
        assertThat(iban.hasValidChecksum()).isTrue();
    }

    @Test
    void generatesDistinctChecksumValidIbans() {
        var generator = new SecureRandomIbanGenerator();
        Set<String> generated = new LinkedHashSet<>();

        for (int i = 0; i < SAMPLE_SIZE; i++) {
            generated.add(generator.generate().value());
        }

        // CreateAccountUseCaseImpl retries only 5 times before failing, so a
        // repeating RNG turns account creation into an outage. A single-sample
        // test cannot see it.
        assertThat(generated).hasSize(SAMPLE_SIZE);
        // Format and MOD 97 checksum validated by the domain type, not a regex.
        for (String value : generated) {
            assertThatCode(() -> Iban.checked(value)).doesNotThrowAnyException();
        }
    }
}
